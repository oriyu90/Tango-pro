# Guest runtime boundary

The local API 35 Google system image is a development PoC only. This directory will hold only manifest pins, product definitions, overlays, SELinux policy, package definitions, and scripts for a separately built AOSP guest.

Do not put an AOSP checkout, SDK image, userdata image, credentials, or generated guest artifact in this repository. A production guest must be rebuilt from its recorded source revisions and pass the Windows runtime acceptance gates.
