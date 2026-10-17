# Making a release

1. Raise the version in `build.sh`: `VERSION_CODE` by one, and
   `VERSION_NAME`. Raise it on the project page too (`docs/index.html`,
   the chip at the top).
2. Add a section for the version to `CHANGELOG.md`.
3. Build:

   ```sh
   export ANDROID_JAR=/path/to/android.jar
   export KEYSTORE=/path/to/your.keystore KS_ALIAS=yourkey KS_PASS=yourpassword
   ./build.sh
   ```

4. Check the signature. The certificate digest must be the same as in every
   earlier release, or phones will refuse the update:

   ```sh
   apksigner verify --print-certs out/pastille-1.1.apk
   ```

5. Commit and push. Then, on the repository page, draft a new release with a
   new tag such as `v1.1`, paste the version's section of the changelog, and
   attach the APK.
6. The project page is the `docs` folder. Once, in the repository settings,
   set the pages source to the `docs` folder of the main branch.

The signing key never goes into the repository: `.gitignore` keeps keystores
and packages out. Keep a copy of the key somewhere safe; without it there are
no updates, only new installs.
