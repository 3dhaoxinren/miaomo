"""Locate Android SDK and JDK without machine-specific paths."""
import os
import pathlib
import shutil

sdk_path = os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
if not sdk_path:
    raise SystemExit('Set ANDROID_SDK_ROOT or ANDROID_HOME to your Android SDK directory.')
SDK = pathlib.Path(sdk_path)
java_path = os.environ.get('JAVA_HOME')
if not java_path:
    compiler = shutil.which('javac')
    if not compiler:
        raise SystemExit('Install JDK 17+ and set JAVA_HOME.')
    java_path = pathlib.Path(compiler).resolve().parents[1]
JDK = pathlib.Path(java_path)
BT = SDK / 'build-tools/35.0.1'
ANDROID = SDK / 'platforms/android-35/android.jar'
if not ANDROID.is_file() or not BT.is_dir():
    raise SystemExit('Install SDK packages: platforms;android-35 and build-tools;35.0.1')

def sdktool(name):
    extension = ('.bat' if name in ('d8', 'apksigner') else '.exe') if os.name == 'nt' else ''
    return BT / (name + extension)

def javatool(name):
    return JDK / 'bin' / (name + ('.exe' if os.name == 'nt' else ''))
