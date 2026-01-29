# Changelog - Sundhed.dk Header Extractor

## Version 1.1.0 - YAML Config Support

### ✨ New Features

#### YAML Configuration Export
- **Copy YAML Config** button - Copy formatted application.yml content to clipboard
- **Download application.yml** button - Download ready-to-use config file
- Automatic YAML formatting and escaping
- Complete application.yml template with all headers

#### Automated Update Script
- `update-headers.sh` script for one-command updates
- Automatic backup of existing configuration
- Smart file detection and cleanup
- Works seamlessly with download button

#### Enhanced UI
- New button group for YAML operations
- Updated instructions with YAML workflow
- Visual feedback for downloads
- Better button organization

### 🐛 Bug Fixes

#### Cookie Capture (v1.0.1)
- Fixed cookie capture using Chrome Cookies API
- Added `extraHeaders` permission for full cookie access
- Improved cookie string formatting
- Better logging for debugging

### 📚 Documentation
- **YAML-UPDATE-GUIDE.md** - Complete guide for YAML workflow
- Security best practices
- Troubleshooting tips
- Example workflows

---

## Version 1.0.0 - Initial Release

### Features
- Automatic header capture from sundhed.dk
- Real-time monitoring of:
  - Cookie
  - X-XSRF-Token
  - Conversation-UUID
  - User-Agent
- Export formats:
  - Environment variables
  - Docker run command
- Manual refresh button
- Debug cookie viewer
- Local-only storage (no external requests)

### Components
- Background service worker for request monitoring
- Content script for sundhed.dk integration
- Popup interface with status indicators
- Chrome storage for persistent headers

---

## Roadmap

### Future Enhancements
- [ ] Auto-refresh headers on sundhed.dk page load
- [ ] Session expiration warnings
- [ ] Support for multiple profiles
- [ ] Header validation before export
- [ ] Integration with dhroxy CLI
- [ ] Automatic detection of dhroxy project folder
