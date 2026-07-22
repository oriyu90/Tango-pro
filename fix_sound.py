import re

with open("app/src/main/java/com/example/service/SoundPlayer.kt", "r") as f:
    text = f.read()

text = re.sub(
    r'val audioTrack = AudioTrack\.Builder\(\)([\s\S]*?)\.build\(\)\n\s+audioTrack\.write\(generatedSnd, 0, numSamples\)\n\s+audioTrack\.play\(\)\n\s+kotlinx\.coroutines\.delay\(\(durationSeconds \* 1000\)\.toLong\(\) \+ 100\)\n\s+audioTrack\.release\(\)',
    r'val audioTrack = AudioTrack.Builder()\1.build()\n            try {\n                audioTrack.write(generatedSnd, 0, numSamples)\n                audioTrack.play()\n                kotlinx.coroutines.delay((durationSeconds * 1000).toLong() + 100)\n            } finally {\n                audioTrack.release()\n            }',
    text
)

with open("app/src/main/java/com/example/service/SoundPlayer.kt", "w") as f:
    f.write(text)

