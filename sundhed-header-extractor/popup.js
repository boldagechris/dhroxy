// State management
let headers = {
  cookie: null,
  xsrf: null,
  uuid: null,
  userAgent: null
};

// Load headers from storage on popup open
async function loadHeaders() {
  const stored = await chrome.storage.local.get(['sundhedHeaders']);
  if (stored.sundhedHeaders) {
    headers = stored.sundhedHeaders;
    updateUI();
  }
}

// Update UI with current headers
function updateUI() {
  const cookieEl = document.getElementById('cookie');
  const xsrfEl = document.getElementById('xsrf');
  const uuidEl = document.getElementById('uuid');
  const userAgentEl = document.getElementById('userAgent');
  const statusEl = document.getElementById('status');
  const copyEnvBtn = document.getElementById('copyEnv');
  const copyDockerBtn = document.getElementById('copyDocker');

  // Update cookie
  if (headers.cookie) {
    cookieEl.textContent = headers.cookie;
    cookieEl.classList.remove('empty');
  } else {
    cookieEl.textContent = 'Not captured yet';
    cookieEl.classList.add('empty');
  }

  // Update XSRF token
  if (headers.xsrf) {
    xsrfEl.textContent = headers.xsrf;
    xsrfEl.classList.remove('empty');
  } else {
    xsrfEl.textContent = 'Not captured yet';
    xsrfEl.classList.add('empty');
  }

  // Update UUID
  if (headers.uuid) {
    uuidEl.textContent = headers.uuid;
    uuidEl.classList.remove('empty');
  } else {
    uuidEl.textContent = 'Not captured yet';
    uuidEl.classList.add('empty');
  }

  // Update User-Agent
  if (headers.userAgent) {
    userAgentEl.textContent = headers.userAgent;
    userAgentEl.classList.remove('empty');
  } else {
    userAgentEl.textContent = 'Not captured yet';
    userAgentEl.classList.add('empty');
  }

  // Update status and button states
  const allCaptured = headers.cookie && headers.xsrf && headers.uuid && headers.userAgent;

  const copyYamlBtn = document.getElementById('copyYaml');
  const downloadYamlBtn = document.getElementById('downloadYaml');

  if (allCaptured) {
    statusEl.textContent = '✓ All headers captured successfully!';
    statusEl.className = 'status success';
    copyEnvBtn.disabled = false;
    copyDockerBtn.disabled = false;
    if (copyYamlBtn) copyYamlBtn.disabled = false;
    if (downloadYamlBtn) downloadYamlBtn.disabled = false;
  } else {
    const missing = [];
    if (!headers.cookie) missing.push('Cookie');
    if (!headers.xsrf) missing.push('X-XSRF-Token');
    if (!headers.uuid) missing.push('Conversation-UUID');
    if (!headers.userAgent) missing.push('User-Agent');

    statusEl.textContent = `⚠ Missing: ${missing.join(', ')}`;
    statusEl.className = 'status warning';
    copyEnvBtn.disabled = true;
    copyDockerBtn.disabled = true;
    if (copyYamlBtn) copyYamlBtn.disabled = true;
    if (downloadYamlBtn) downloadYamlBtn.disabled = true;
  }
}

// Generate environment variables format
function generateEnvVars() {
  return `export SUNDHED_STATIC_COOKIE='${headers.cookie}'
export SUNDHED_STATIC_X_XSRF_TOKEN='${headers.xsrf}'
export SUNDHED_STATIC_CONVERSATION_UUID='${headers.uuid}'
export SUNDHED_STATIC_USER_AGENT='${headers.userAgent}'`;
}

// Generate Docker run command
function generateDockerCommand() {
  return `docker run -p 8080:8080 \\
  -e SUNDHED_STATIC_COOKIE='${headers.cookie}' \\
  -e SUNDHED_STATIC_X_XSRF_TOKEN='${headers.xsrf}' \\
  -e SUNDHED_STATIC_CONVERSATION_UUID='${headers.uuid}' \\
  -e SUNDHED_STATIC_USER_AGENT='${headers.userAgent}' \\
  dhroxy`;
}

// Generate application.yml content
function generateYamlConfig() {
  // Escape special characters in YAML strings
  const escapeYaml = (str) => {
    if (!str) return '""';
    // If string contains special chars, quote it
    if (str.includes(':') || str.includes('"') || str.includes("'") ||
        str.includes('\n') || str.includes('#') || str.includes('=')) {
      return `"${str.replace(/"/g, '\\"')}"`;
    }
    return `"${str}"`;
  };

  return `server:
  port: 8080

sundhed:
  client:
    base-url: "https://www.sundhed.dk"
    connect-timeout: 5s
    read-timeout: 20s
    forwarded-headers:
      #- accept
    static-headers:
      user-agent: ${escapeYaml(headers.userAgent)}
      conversation-uuid: ${escapeYaml(headers.uuid)}
      x-xsrf-token: ${escapeYaml(headers.xsrf)}
      cookie: ${escapeYaml(headers.cookie)}

spring:
  main:
    web-application-type: servlet
  ai:
    mcp:
      server:
        enabled: true

logging:
  level:
    dhroxy: DEBUG
    org.springframework.web: DEBUG
`;
}

// Download YAML as file
function downloadYaml() {
  const yamlContent = generateYamlConfig();
  const blob = new Blob([yamlContent], { type: 'text/yaml' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'application.yml';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);

  const feedback = document.getElementById('copyFeedback');
  feedback.textContent = '✓ application.yml downloaded!';
  feedback.classList.add('show');
  setTimeout(() => {
    feedback.classList.remove('show');
    feedback.textContent = '✓ Copied to clipboard!';
  }, 2000);
}

// Copy to clipboard and show feedback
async function copyToClipboard(text) {
  try {
    await navigator.clipboard.writeText(text);
    const feedback = document.getElementById('copyFeedback');
    feedback.classList.add('show');
    setTimeout(() => {
      feedback.classList.remove('show');
    }, 2000);
  } catch (err) {
    console.error('Failed to copy:', err);
    alert('Failed to copy to clipboard');
  }
}

// Request fresh headers from content script
async function refreshHeaders() {
  const statusEl = document.getElementById('status');
  statusEl.textContent = '🔄 Refreshing headers...';
  statusEl.className = 'status warning';

  try {
    // First, try to refresh cookies directly from background
    const cookieResponse = await chrome.runtime.sendMessage({
      action: 'refreshCookies'
    });

    console.log('Cookie refresh response:', cookieResponse);

    // Query for active sundhed.dk tabs
    const tabs = await chrome.tabs.query({
      active: true,
      url: 'https://www.sundhed.dk/*'
    });

    if (tabs.length === 0) {
      // Even if no active tab, we might have gotten cookies
      if (cookieResponse && cookieResponse.success) {
        statusEl.textContent = '⚠ Cookies captured, but please open sundhed.dk for other headers';
        statusEl.className = 'status warning';
        await loadHeaders();
      } else {
        statusEl.textContent = '❌ Please open sundhed.dk first';
        statusEl.className = 'status error';
      }
      return;
    }

    // Send message to content script to capture other headers
    try {
      const response = await chrome.tabs.sendMessage(tabs[0].id, {
        action: 'captureHeaders'
      });

      if (response && response.success) {
        // Headers will be updated via storage listener
        statusEl.textContent = '✓ Headers refreshed!';
        statusEl.className = 'status success';

        // Reload from storage
        await loadHeaders();
      } else {
        statusEl.textContent = '⚠ Could not capture all headers';
        statusEl.className = 'status warning';
        await loadHeaders();
      }
    } catch (contentScriptError) {
      // Content script might not be ready, but we got cookies
      console.log('Content script error, but continuing:', contentScriptError);
      if (cookieResponse && cookieResponse.success) {
        statusEl.textContent = '⚠ Cookies captured. Reload sundhed.dk page for other headers';
        statusEl.className = 'status warning';
      } else {
        statusEl.textContent = '❌ Please reload the sundhed.dk page';
        statusEl.className = 'status error';
      }
      await loadHeaders();
    }
  } catch (err) {
    console.error('Refresh error:', err);
    statusEl.textContent = '❌ Error: Make sure you are logged into sundhed.dk';
    statusEl.className = 'status error';
    await loadHeaders();
  }
}

// Event listeners
document.getElementById('copyEnv').addEventListener('click', () => {
  copyToClipboard(generateEnvVars());
});

document.getElementById('copyDocker').addEventListener('click', () => {
  copyToClipboard(generateDockerCommand());
});

document.getElementById('copyYaml').addEventListener('click', () => {
  copyToClipboard(generateYamlConfig());
});

document.getElementById('downloadYaml').addEventListener('click', () => {
  downloadYaml();
});

document.getElementById('refresh').addEventListener('click', refreshHeaders);

document.getElementById('debugCookies').addEventListener('click', async () => {
  const statusEl = document.getElementById('status');
  statusEl.textContent = '🔍 Fetching all cookies...';
  statusEl.className = 'status warning';

  try {
    // Get cookies from multiple domains
    const domains = ['.sundhed.dk', 'sundhed.dk', 'www.sundhed.dk'];
    let allCookies = [];

    for (const domain of domains) {
      try {
        const cookies = await chrome.cookies.getAll({ domain: domain });
        allCookies.push(...cookies);
      } catch (e) {
        console.log(`Could not get cookies for ${domain}:`, e);
      }
    }

    if (allCookies.length === 0) {
      statusEl.textContent = '❌ No cookies found! Are you logged into sundhed.dk?';
      statusEl.className = 'status error';
      alert('No cookies found for sundhed.dk. Please:\n1. Go to sundhed.dk\n2. Log in\n3. Try again');
      return;
    }

    // Show all cookies in console and alert
    console.log('All sundhed.dk cookies:', allCookies);

    const cookieList = allCookies
      .map(c => `${c.name} = ${c.value.substring(0, 50)}... (domain: ${c.domain})`)
      .join('\n\n');

    alert(`Found ${allCookies.length} cookies:\n\n${cookieList}`);

    // Format as cookie string
    const cookieString = allCookies.map(c => `${c.name}=${c.value}`).join('; ');
    console.log('Cookie string:', cookieString);

    statusEl.textContent = `✓ Found ${allCookies.length} cookies (check console for details)`;
    statusEl.className = 'status success';

  } catch (err) {
    console.error('Debug error:', err);
    statusEl.textContent = '❌ Error getting cookies';
    statusEl.className = 'status error';
    alert('Error: ' + err.message);
  }
});

// Listen for storage changes
chrome.storage.onChanged.addListener((changes, areaName) => {
  if (areaName === 'local' && changes.sundhedHeaders) {
    headers = changes.sundhedHeaders.newValue || {};
    updateUI();
  }
});

// Initialize on load
loadHeaders();
