# YAML Configuration Update Guide

## 🎯 Quick Method: Download & Replace

### 1. Capture Headers
1. Open sundhed.dk and log in
2. Click the extension icon
3. Click "🔄 Refresh Headers"
4. Wait for green "✓ All headers captured successfully!"

### 2. Download Configuration
Click **"Download application.yml"** button

The file will be downloaded to your Downloads folder.

### 3. Update Your Project

**Option A: Automatic Script** (Recommended)
```bash
cd dhroxy
./update-headers.sh
```

The script will:
- Find the downloaded application.yml
- Backup your existing file
- Replace with new configuration
- Clean up the downloaded file

**Option B: Manual Copy**
```bash
cd dhroxy
# Backup existing config
cp src/main/resources/application.yml src/main/resources/application.yml.backup

# Copy downloaded file
cp ~/Downloads/application.yml src/main/resources/application.yml
```

### 4. Run dhroxy
```bash
./gradlew bootRun
```

Your dhroxy is now authenticated with sundhed.dk! 🎉

---

## 📋 Alternative Method: Copy YAML Config

If you prefer to edit manually:

1. Click **"Copy YAML Config"** button
2. Open `src/main/resources/application.yml` in your editor
3. Replace the `sundhed.client.static-headers` section
4. Save the file

---

## 🔄 When to Update Headers

Update your configuration when:
- ✅ Headers have expired (dhroxy returns 401/403 errors)
- ✅ Your sundhed.dk session has timed out
- ✅ You log into sundhed.dk on a new browser/device
- ✅ XSRF token or session ID has changed

**Typical session lifetime:** 15-30 minutes of inactivity

---

## 📝 What Gets Updated

The downloaded application.yml includes:

```yaml
sundhed:
  client:
    static-headers:
      user-agent: "Mozilla/5.0 (..."        # Your browser UA
      conversation-uuid: "96fb3d61-..."     # Session UUID
      x-xsrf-token: "277ff1af17..."         # CSRF token
      cookie: "sdk-user-accept-cookies=..." # All cookies
```

All other configuration remains unchanged:
- Server port
- Timeouts
- Spring configuration
- Logging levels

---

## 🛠️ Troubleshooting

### "Headers expired" error
Your sundhed.dk session timed out. Refresh headers and update config again.

### "File not found" in update script
Make sure you clicked "Download application.yml" first. Check ~/Downloads/ folder.

### YAML syntax errors
Use "Download application.yml" instead of "Copy YAML Config" to avoid formatting issues.

### Headers work in browser but not in dhroxy
Make sure you:
1. Downloaded the latest headers
2. Replaced the application.yml file
3. Restarted dhroxy (./gradlew bootRun)

---

## 🔐 Security Notes

**⚠️ NEVER commit application.yml with real headers to git!**

The headers contain your authentication credentials. To protect them:

### Option 1: Use Environment Variables (Production)
Instead of static-headers in YAML, use ENV vars:
```bash
export SUNDHED_STATIC_COOKIE='...'
export SUNDHED_STATIC_X_XSRF_TOKEN='...'
export SUNDHED_STATIC_CONVERSATION_UUID='...'
export SUNDHED_STATIC_USER_AGENT='...'
./gradlew bootRun
```

### Option 2: Gitignore Local Config (Development)
```bash
# Add to .gitignore
src/main/resources/application-local.yml

# Use Spring profile
./gradlew bootRun --args='--spring.profiles.active=local'
```

### Option 3: Use .env Files
```bash
# Create .env file (already in .gitignore)
echo 'SUNDHED_STATIC_COOKIE=...' > .env
source .env
./gradlew bootRun
```

---

## 💡 Tips

1. **Keep extension open:** The extension continues capturing headers in the background
2. **Refresh regularly:** Update headers before starting a long development session
3. **Use the script:** `./update-headers.sh` is the fastest way to update
4. **Check logs:** Look for "401" or "403" errors in dhroxy logs when headers expire
5. **Backup configs:** The update script automatically creates backups with timestamps

---

## 📖 Examples

### Complete Workflow
```bash
# 1. Capture headers (in browser extension)
# 2. Download application.yml
# 3. Update and run
cd dhroxy
./update-headers.sh
./gradlew bootRun

# 4. Test
curl http://localhost:8080/fhir/Patient
```

### Quick Verification
```bash
# Check if headers are in config
grep "x-xsrf-token" src/main/resources/application.yml

# Verify it's recent (not expired)
stat src/main/resources/application.yml
```

### Manual YAML Update
```bash
# 1. Copy YAML Config from extension
# 2. Edit file
vim src/main/resources/application.yml

# 3. Replace the static-headers section
# 4. Save and restart
./gradlew bootRun
```
