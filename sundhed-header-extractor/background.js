// Background service worker for capturing network requests

// Store captured headers
let capturedHeaders = {
  cookie: null,
  xsrf: null,
  uuid: null,
  userAgent: null
};

// Function to get all cookies from sundhed.dk and format as cookie header
async function getCookiesForDomain() {
  try {
    const cookies = await chrome.cookies.getAll({ domain: '.sundhed.dk' });
    if (cookies.length === 0) {
      // Try without the dot
      const cookies2 = await chrome.cookies.getAll({ domain: 'sundhed.dk' });
      if (cookies2.length === 0) {
        return null;
      }
      return cookies2.map(c => `${c.name}=${c.value}`).join('; ');
    }
    return cookies.map(c => `${c.name}=${c.value}`).join('; ');
  } catch (error) {
    console.error('Error getting cookies:', error);
    return null;
  }
}

// Listen for web requests to sundhed.dk API endpoints
chrome.webRequest.onBeforeSendHeaders.addListener(
  async (details) => {
    if (!details.requestHeaders) return;

    let updated = false;

    // Extract headers from the request - INCLUDING cookie from request headers
    for (const header of details.requestHeaders) {
      const name = header.name.toLowerCase();
      const value = header.value;

      if (name === 'cookie' && value) {
        // Capture cookie from request header (this is the complete cookie string)
        if (capturedHeaders.cookie !== value) {
          capturedHeaders.cookie = value;
          updated = true;
          console.log('Cookie captured from request header:', value.substring(0, 100) + '...');
        }
      } else if (name === 'x-xsrf-token' && value) {
        if (capturedHeaders.xsrf !== value) {
          capturedHeaders.xsrf = value;
          updated = true;
        }
      } else if (name === 'conversation-uuid' && value) {
        if (capturedHeaders.uuid !== value) {
          capturedHeaders.uuid = value;
          updated = true;
        }
      } else if (name === 'user-agent' && value) {
        if (capturedHeaders.userAgent !== value) {
          capturedHeaders.userAgent = value;
          updated = true;
        }
      }
    }

    // If we didn't get cookie from headers, try the Cookies API as fallback
    if (!capturedHeaders.cookie) {
      const cookieString = await getCookiesForDomain();
      if (cookieString) {
        capturedHeaders.cookie = cookieString;
        updated = true;
        console.log('Cookie captured from Cookies API (fallback)');
      }
    }

    // Save to storage if headers were updated
    if (updated) {
      chrome.storage.local.set({ sundhedHeaders: capturedHeaders });
      console.log('=== Headers captured ===');
      console.log('Cookie:', capturedHeaders.cookie ?
        capturedHeaders.cookie.substring(0, 150) + '...' : 'MISSING');
      console.log('XSRF:', capturedHeaders.xsrf || 'MISSING');
      console.log('UUID:', capturedHeaders.uuid || 'MISSING');
      console.log('User-Agent:', capturedHeaders.userAgent ?
        capturedHeaders.userAgent.substring(0, 50) + '...' : 'MISSING');
      console.log('=======================');
    }
  },
  {
    urls: [
      'https://www.sundhed.dk/app/*',
      'https://www.sundhed.dk/api/*',
      'https://www.sundhed.dk/*'
    ]
  },
  ['requestHeaders', 'extraHeaders']
);

// Listen for messages from popup or content script
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === 'getHeaders') {
    sendResponse({ headers: capturedHeaders });
  } else if (request.action === 'clearHeaders') {
    capturedHeaders = {
      cookie: null,
      xsrf: null,
      uuid: null,
      userAgent: null
    };
    chrome.storage.local.set({ sundhedHeaders: capturedHeaders });
    sendResponse({ success: true });
  } else if (request.action === 'refreshCookies') {
    // Manually refresh cookies
    getCookiesForDomain().then(cookieString => {
      if (cookieString) {
        capturedHeaders.cookie = cookieString;
        chrome.storage.local.set({ sundhedHeaders: capturedHeaders });
        sendResponse({ success: true, cookie: cookieString });
      } else {
        sendResponse({ success: false, error: 'No cookies found' });
      }
    });
    return true; // Keep channel open for async response
  }
  return true;
});

// Load stored headers on startup
chrome.storage.local.get(['sundhedHeaders'], (result) => {
  if (result.sundhedHeaders) {
    capturedHeaders = result.sundhedHeaders;
    console.log('Loaded stored headers on startup');
  }
});

// Keep service worker alive and refresh cookies periodically
chrome.runtime.onInstalled.addListener(() => {
  console.log('Sundhed.dk Header Extractor installed');

  // Try to get cookies on install
  getCookiesForDomain().then(cookieString => {
    if (cookieString) {
      capturedHeaders.cookie = cookieString;
      chrome.storage.local.set({ sundhedHeaders: capturedHeaders });
      console.log('Cookies captured on install');
    }
  });
});

// Listen for cookie changes
chrome.cookies.onChanged.addListener((changeInfo) => {
  if (changeInfo.cookie.domain.includes('sundhed.dk')) {
    console.log('Cookie changed on sundhed.dk, refreshing...');
    getCookiesForDomain().then(cookieString => {
      if (cookieString) {
        capturedHeaders.cookie = cookieString;
        chrome.storage.local.set({ sundhedHeaders: capturedHeaders });
      }
    });
  }
});
