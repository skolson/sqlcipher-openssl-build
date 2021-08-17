##GradleSqlCipher Change log

###Version history

### 0.2.0 2021-08-15
*Added*

- Support for Gradle 7.x by improving labeling of properties in tasks to conform to the new requirements.
- Tested/built with Gradle 7.1.1
- Support for OpenSSL 3.x
- Support for NDK versions r22 and r23 which have slightly changed directory structures
- Make separate source directories for each build type, for both OpenSSL and SqlCipher.  Both build processes use the source directory and subdirectories within it as working/output diurectories. Keeping each build type in separate source directories retains all the intermediate output files for potential use, and avoids gradle 7.x warnings about overlapping output directories across build tasks. 

*Changed*

- Replaced ANDROID_SDK_HOME usage with ANDROID_SDK_ROOT

### 0.1.1 2020-11-12

Minor fix

### 0.1.0 2020-10-06

Original release, using Gradle 6.6.1