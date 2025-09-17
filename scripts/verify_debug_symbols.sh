#!/bin/bash

# Debug Symbols Verification Script
# This script verifies that debug symbols are properly configured for Android App Bundles

echo "🔍 Debug Symbols Configuration Verification"
echo "============================================"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if we're in the project root
if [ ! -f "app/build.gradle.kts" ]; then
    echo -e "${RED}❌ Error: Please run this script from the project root directory${NC}"
    exit 1
fi

echo -e "${YELLOW}📋 Checking Gradle configuration...${NC}"

# Check build.gradle.kts for debug symbols configuration
echo "1. Checking NDK version specification..."
if grep -q "ndkVersion.*=.*\".*\"" app/build.gradle.kts; then
    echo -e "${GREEN}✅ NDK version is explicitly set${NC}"
else
    echo -e "${RED}❌ NDK version not found in build.gradle.kts${NC}"
fi

echo "2. Checking native symbol upload configuration..."
if grep -q "nativeSymbolUploadEnabled.*=.*true" app/build.gradle.kts; then
    echo -e "${GREEN}✅ Native symbol upload is enabled${NC}"
else
    echo -e "${RED}❌ Native symbol upload not properly configured${NC}"
fi

echo "3. Checking symbol stripping prevention..."
if grep -q "doNotStrip" app/build.gradle.kts; then
    echo -e "${GREEN}✅ Symbol stripping prevention is configured${NC}"
    echo "   Checking supported ABIs..."
    for abi in "arm64-v8a" "armeabi-v7a" "x86" "x86_64"; do
        if grep -q "doNotStrip.*$abi" app/build.gradle.kts; then
            echo -e "${GREEN}   ✅ $abi symbols preserved${NC}"
        else
            echo -e "${YELLOW}   ⚠️  $abi symbols may not be preserved${NC}"
        fi
    done
else
    echo -e "${RED}❌ Symbol stripping prevention not configured${NC}"
fi

echo "4. Checking App Bundle configuration..."
if grep -q "bundle.*{" app/build.gradle.kts; then
    echo -e "${GREEN}✅ App Bundle configuration found${NC}"
else
    echo -e "${YELLOW}⚠️  App Bundle configuration not found${NC}"
fi

echo "5. Checking native dependencies..."
if grep -q "porcupine-android" app/build.gradle.kts; then
    echo -e "${GREEN}✅ Picovoice Porcupine dependency found${NC}"
else
    echo -e "${YELLOW}⚠️  Picovoice dependency not found${NC}"
fi

if grep -q "firebase-crashlytics-ndk" app/build.gradle.kts; then
    echo -e "${GREEN}✅ Firebase Crashlytics NDK dependency found${NC}"
else
    echo -e "${RED}❌ Firebase Crashlytics NDK dependency not found${NC}"
fi

echo ""
echo -e "${YELLOW}🛠️  Build verification...${NC}"

# Check if gradlew exists and is executable
if [ -x "./gradlew" ]; then
    echo "6. Gradle wrapper found and executable"
    
    # Try to run a dry-run to check for configuration errors
    echo "7. Running configuration check..."
    if ./gradlew help --no-daemon &>/dev/null; then
        echo -e "${GREEN}✅ Gradle configuration is valid${NC}"
    else
        echo -e "${RED}❌ Gradle configuration has errors${NC}"
        echo "   Run './gradlew help' to see detailed errors"
    fi
else
    echo -e "${RED}❌ Gradle wrapper not found or not executable${NC}"
fi

echo ""
echo -e "${YELLOW}📁 File structure verification...${NC}"

# Check for required files
echo "8. Checking required files..."
required_files=(
    "app/build.gradle.kts"
    "gradle/libs.versions.toml"
    "google-services.json"
    "local.properties"
)

for file in "${required_files[@]}"; do
    if [ -f "$file" ]; then
        echo -e "${GREEN}✅ $file exists${NC}"
    else
        if [ "$file" == "google-services.json" ]; then
            echo -e "${YELLOW}⚠️  $file not found (required for Firebase features)${NC}"
        elif [ "$file" == "local.properties" ]; then
            echo -e "${YELLOW}⚠️  $file not found (copy from local.properties.template)${NC}"
        else
            echo -e "${RED}❌ $file not found${NC}"
        fi
    fi
done

echo ""
echo -e "${YELLOW}🚀 Next steps:${NC}"
echo "1. Build release bundle: ./gradlew bundleRelease"
echo "2. Upload to Google Play Console"
echo "3. Check 'App Bundle Explorer' for native libraries"
echo "4. Verify crash reports include symbolicated stack traces"

echo ""
echo -e "${GREEN}✨ Verification complete!${NC}"
echo "If all checks pass, your debug symbols should be properly configured."