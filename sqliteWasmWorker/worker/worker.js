/**
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sqlite3InitModule from '@sqlite.org/sqlite-wasm';

let sqlite3 = null;
const databases = new Map();
const statements = new Map();
let nextDatabaseId = 0;
let nextStatementId = 0;

function errorMessage(error) {
  return error instanceof Error ? error.message : String(error);
}

function openRequest(id, requestData) {
  try {
    const databaseId = nextDatabaseId++;
    const database = requestData.fileName === ':memory:'
      ? new sqlite3.oo1.DB(':memory:', 'ct')
      : (() => {
          if (!sqlite3.oo1.OpfsDb) throw new Error('OPFS SQLite is unavailable in this browser.');
          return new sqlite3.oo1.OpfsDb(requestData.fileName);
        })();
    databases.set(databaseId, database);
    postMessage({id, data: {databaseId}});
  } catch (error) {
    postMessage({id, error: errorMessage(error)});
  }
}

function prepareRequest(id, requestData) {
  try {
    const database = databases.get(requestData.databaseId);
    if (!database) throw new Error(`Invalid database ID: ${requestData.databaseId}`);
    const statementId = nextStatementId++;
    const statement = database.prepare(requestData.sql);
    statements.set(statementId, statement);
    const columnNames = [];
    for (let index = 0; index < statement.columnCount; index += 1) {
      columnNames.push(sqlite3.capi.sqlite3_column_name(statement, index));
    }
    postMessage({id, data: {
      statementId,
      parameterCount: sqlite3.capi.sqlite3_bind_parameter_count(statement),
      columnNames,
    }});
  } catch (error) {
    postMessage({id, error: errorMessage(error)});
  }
}

function stepRequest(id, requestData) {
  const statement = statements.get(requestData.statementId);
  if (!statement) {
    postMessage({id, error: `Invalid statement ID: ${requestData.statementId}`});
    return;
  }
  try {
    const rows = [];
    const columnTypes = [];
    statement.reset();
    statement.clearBindings();
    requestData.bindings.forEach((binding, index) => statement.bind(index + 1, binding));
    while (statement.step()) {
      if (columnTypes.length === 0) {
        for (let index = 0; index < statement.columnCount; index += 1) {
          columnTypes.push(sqlite3.capi.sqlite3_column_type(statement, index));
        }
      }
      rows.push(statement.get([]));
    }
    postMessage({id, data: {rows, columnTypes}});
  } catch (error) {
    postMessage({id, error: errorMessage(error)});
  }
}

function closeRequest(id, requestData) {
  try {
    if (requestData.statementId !== undefined && requestData.statementId !== null) {
      const statement = statements.get(requestData.statementId);
      if (!statement) throw new Error(`Invalid statement ID: ${requestData.statementId}`);
      statement.finalize();
      statements.delete(requestData.statementId);
    }
    if (requestData.databaseId !== undefined && requestData.databaseId !== null) {
      const database = databases.get(requestData.databaseId);
      if (!database) throw new Error(`Invalid database ID: ${requestData.databaseId}`);
      database.close();
      databases.delete(requestData.databaseId);
    }
    // WebWorkerSQLiteDriver sends close as a one-way request. A success reply would
    // therefore have no matching pending request and is treated as a protocol error.
    // Errors are still posted so the driver can fail any in-flight operations.
  } catch (error) {
    postMessage({id, error: errorMessage(error)});
  }
}

const commandMap = {open: openRequest, prepare: prepareRequest, step: stepRequest, close: closeRequest};

function handleMessage(event) {
  const request = event.data;
  if (!request || !request.data || typeof request.data.cmd !== 'string') {
    postMessage({id: request?.id, error: 'Invalid SQLite worker request.'});
    return;
  }
  const handler = commandMap[request.data.cmd];
  if (!handler) {
    postMessage({id: request.id, error: `Unknown SQLite worker command: ${request.data.cmd}`});
    return;
  }
  handler(request.id, request.data);
}

const pendingMessages = [];
onmessage = event => sqlite3 ? handleMessage(event) : pendingMessages.push(event);

sqlite3InitModule().then(instance => {
  sqlite3 = instance;
  while (pendingMessages.length > 0) handleMessage(pendingMessages.shift());
}).catch(error => {
  const message = errorMessage(error);
  while (pendingMessages.length > 0) {
    const event = pendingMessages.shift();
    postMessage({id: event?.data?.id, error: message});
  }
});
