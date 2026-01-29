# 🏥 Sundhed.dk Header Extractor

A Chrome/Edge browser extension that captures authentication headers from sundhed.dk for use with the dhroxy FHIR proxy.

## Features

- ✅ Automatically captures headers from sundhed.dk when you're logged in
- ✅ Real-time monitoring of Cookie, X-XSRF-Token, Conversation-UUID, and User-Agent
- ✅ One-click copy as environment variables or Docker command
- ✅ No manual header extraction needed
- ✅ Privacy-focused: all data stays local in your browser

## Installation

### Chrome / Chromium / Edge

1. **Download or clone this extension folder**
   ```bash
   # If you have the dhroxy repo
   cd dhroxy/sundhed-header-extractor

   # Or download just this folder to your computer
   ```

2. **Open your browser's extension page**
   - Chrome: Go to `chrome://extensions/`
   - Edge: Go to `edge://extensions/`

3. **Enable Developer Mode**
   - Toggle the "Developer mode" switch in the top-right corner

4. **Load the extension**
   - Click "Load unpacked"
   - Select the `sundhed-header-extractor` folder
   - The extension icon should appear in your browser toolbar

## Usage

### Step 1: Log into sundhed.dk

1. Navigate to https://www.sundhed.dk
2. Log in with MitID or your credentials
3. The extension will automatically start capturing headers in the background

### Step 2: Capture Headers

1. Click the extension icon in your browser toolbar
2. Click the **"🔄 Refresh Headers"** button
3. Wait for the status to show "✓ All headers captured successfully!"

### Step 3: Copy Headers

Choose one of two options:

**Option A: Copy as Environment Variables**
- Click "Copy as ENV Variables"
- Paste into your terminal:
  ```bash
  # Paste the copied content
  export SUNDHED_STATIC_COOKIE='...'
  export SUNDHED_STATIC_X_XSRF_TOKEN='...'
  export SUNDHED_STATIC_CONVERSATION_UUID='...'
  export SUNDHED_STATIC_USER_AGENT='...'

  # Then run dhroxy
  ./gradlew bootRun
  ```

**Option B: Copy Docker Command**
- Click "Copy Docker Command"
- Paste into your terminal:
  ```bash
  # The command is ready to run
  docker run -p 8080:8080 \
    -e SUNDHED_STATIC_COOKIE='...' \
    -e SUNDHED_STATIC_X_XSRF_TOKEN='...' \
    -e SUNDHED_STATIC_CONVERSATION_UUID='...' \
    -e SUNDHED_STATIC_USER_AGENT='...' \
    dhroxy
  ```

## How It Works

### Background Service Worker
Monitors all HTTP requests to sundhed.dk API endpoints and extracts the required authentication headers automatically.

### Content Script
Runs on sundhed.dk pages and triggers header capture when you refresh or when it detects you're logged in.

### Popup Interface
Displays captured headers and provides convenient copy buttons for different use cases.

## Security & Privacy

- ✅ **Local Only**: All captured data is stored locally in your browser using Chrome's storage API
- ✅ **No External Requests**: The extension never sends data to any external servers
- ✅ **Scoped Permissions**: Only monitors sundhed.dk domains
- ✅ **Read-Only**: Only reads headers, never modifies web requests
- ✅ **Open Source**: All code is visible and auditable in this folder

## Troubleshooting

### "Missing: Cookie, X-XSRF-Token..." warning

**Solution**: Make sure you're logged into sundhed.dk first, then click "Refresh Headers"

### "Please open sundhed.dk first" error

**Solution**: Open a sundhed.dk tab and make sure you're on `https://www.sundhed.dk/*`

### Headers captured but dhroxy still fails

**Possible causes**:
1. **Session expired**: sundhed.dk sessions expire after inactivity. Refresh headers and try again
2. **Cookie too long**: Some shells have limits on environment variable length. Use a `.env` file instead:
   ```bash
   # Save to .env file
   echo "SUNDHED_STATIC_COOKIE='...'" > .env
   echo "SUNDHED_STATIC_X_XSRF_TOKEN='...'" >> .env
   echo "SUNDHED_STATIC_CONVERSATION_UUID='...'" >> .env
   echo "SUNDHED_STATIC_USER_AGENT='...'" >> .env

   # Load and run
   source .env
   ./gradlew bootRun
   ```

### Extension not capturing automatically

**Solution**:
1. Navigate to any sundhed.dk page that makes API calls (e.g., "Min oversigt")
2. Click "Refresh Headers" manually
3. The background service worker will capture headers from the API request

## Development

### Project Structure
```
sundhed-header-extractor/
├── manifest.json       # Extension configuration
├── background.js       # Service worker for header capture
├── content.js          # Runs on sundhed.dk pages
├── popup.html          # Extension popup UI
├── popup.js            # Popup logic and interaction
├── icon16.png          # Extension icons
├── icon48.png
├── icon128.png
└── README.md           # This file
```

### Modifying the Extension

After making changes:
1. Go to `chrome://extensions/`
2. Click the reload icon on the extension card
3. Test your changes

## Alternative: Manual Header Extraction

If you prefer not to use the extension, you can manually extract headers:

1. Open sundhed.dk and log in
2. Open Developer Tools (F12)
3. Go to the Network tab
4. Click on any API request to sundhed.dk
5. Copy the headers from the "Request Headers" section

See the screenshot in `dhroxy/docs/pic.png` for reference.

## License

This extension is part of the dhroxy project and follows the same license (Apache 2.0).

## Support

For issues or questions:
- Check the dhroxy main README: `../readme.md`
- Review the CLAUDE.MD for project context: `../CLAUDE.MD`
- Open an issue in the dhroxy repository
