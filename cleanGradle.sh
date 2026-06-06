#!/bin/bash
echo "Cleaning Gradle caches inside Debian..."
proot-distro login debian -- bash -c "rm -rf /root/.gradle/caches"
echo "Done."
