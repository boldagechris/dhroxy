# Quick Start Guide

## Install in 60 Seconds

### 1. Load Extension (20 seconds)
```bash
# Navigate to extension folder
cd sundhed-header-extractor

# Open Chrome/Edge extensions page
# Chrome: chrome://extensions/
# Edge: edge://extensions/

# Enable "Developer mode" (top right toggle)
# Click "Load unpacked"
# Select this folder
```

### 2. Capture Headers (20 seconds)
```bash
# 1. Go to https://www.sundhed.dk
# 2. Log in with MitID
# 3. Click the extension icon in toolbar
# 4. Click "🔄 Refresh Headers"
```

### 3. Use with dhroxy (20 seconds)
```bash
# Click "Copy as ENV Variables" in the extension
# Paste in terminal, then run:
./gradlew bootRun

# OR click "Copy Docker Command" and paste:
# (It's ready to run immediately)
```

## That's it! 🎉

Your dhroxy FHIR proxy is now authenticated with sundhed.dk.

## Test It

```bash
# List all available FHIR resources
curl http://localhost:8080/fhir/metadata

# Get lab results from 2024
curl http://localhost:8080/fhir/Observation?date=ge2024-01-01

# Get patient info
curl http://localhost:8080/fhir/Patient
```

## Troubleshooting

**Extension won't capture headers?**
- Make sure you're logged into sundhed.dk
- Navigate to "Min oversigt" or any page that loads data
- Click "Refresh Headers" again

**dhroxy returns 401/403 errors?**
- Your sundhed.dk session may have expired
- Go back to sundhed.dk and refresh the page
- Capture headers again

**Headers too long for terminal?**
- Use a .env file instead:
  ```bash
  # In extension, copy as ENV variables
  # Create .env file:
  cat > .env
  # Paste the copied content
  # Press Ctrl+D

  # Load and run
  source .env
  ./gradlew bootRun
  ```
