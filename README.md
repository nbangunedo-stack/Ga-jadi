Monet helper - ready for Codemagic.

Includes:
- Monetization checklist
- Idea generator (coastal/shrimp farm ASMR)
- Title/description/tag generator
- Upload scheduler & reminders
- Export CSV of ideas
- Manual analytics input (subs & watch hours)
- Thumbnail templates
- Simple procedural ASMR sound generator (waves, aerator hum, white noise, tapping) that can play and export WAV locally.

How to build on Codemagic:
1. Push this repository to GitHub (upload all files).
2. Connect repo to Codemagic.
3. Run workflow 'Android Debug APK'.
4. Download app-debug.apk from Artifacts and install on your Android device.

Note: The ASMR generator uses procedural synthesis; for higher-quality samples you can import royalty-free samples into app_files/samples and play them.