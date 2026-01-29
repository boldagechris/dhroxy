// Content script that runs on sundhed.dk pages

// Listen for messages from popup
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === 'captureHeaders') {
    // Trigger a background request to capture headers
    // This works by making the page perform a fetch which the background script will intercept
    captureHeadersFromPage()
      .then(result => sendResponse({ success: true, ...result }))
      .catch(err => sendResponse({ success: false, error: err.message }));
    return true; // Keep channel open for async response
  }
});

// Capture headers by making a lightweight API call
async function captureHeadersFromPage() {
  try {
    // Try to fetch a lightweight endpoint to trigger header capture
    // The background script will intercept this and capture the headers
    const endpoints = [
      '/api/core/userinfo',
      '/api/minlaegeorganization',
      '/app/personvaelgerportal/api/v1/GetPersonSelection'
    ];

    // Try each endpoint until one succeeds
    for (const endpoint of endpoints) {
      try {
        await fetch(`https://www.sundhed.dk${endpoint}`, {
          method: 'GET',
          credentials: 'include',
          headers: {
            'Accept': 'application/json'
          }
        });
        // If successful, headers were captured by background script
        break;
      } catch (e) {
        // Try next endpoint
        continue;
      }
    }

    return { captured: true };
  } catch (error) {
    console.error('Failed to capture headers:', error);
    return { captured: false, error: error.message };
  }
}

// Auto-capture on page load if logged in
window.addEventListener('load', () => {
  // Wait a bit for the page to fully initialize
  setTimeout(() => {
    // Check if user is logged in by looking for common elements
    const isLoggedIn = document.querySelector('[data-role="user-menu"]') !== null ||
                      document.querySelector('.user-info') !== null ||
                      document.cookie.includes('sdk-auth') ||
                      document.cookie.includes('conversation-uuid');

    if (isLoggedIn) {
      console.log('Sundhed.dk: User appears to be logged in, capturing headers...');
      captureHeadersFromPage();
    }
  }, 2000);
});

// Listen for navigation events (SPA navigation)
let lastUrl = location.href;
new MutationObserver(() => {
  const url = location.href;
  if (url !== lastUrl) {
    lastUrl = url;
    // Re-capture headers on navigation
    setTimeout(() => captureHeadersFromPage(), 1000);
  }
}).observe(document, { subtree: true, childList: true });
