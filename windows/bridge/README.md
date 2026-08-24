# TangoBridge boundary

TangoBridge is not implemented in the PoC. When the runtime and viewer are stable, it may transport host files and expose Android document/share plumbing only.

It must not contain study logic, CSV parsing, Room access, score handling, or a parallel implementation of Tango Pro features. CSV and Study Archive bytes are transported unchanged and validated by Tango Pro's existing Android implementation.
