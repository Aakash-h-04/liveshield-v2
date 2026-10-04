/**
 * LiveShield Enterprise Theme Switcher (Dark / Light)
 * Persists in localStorage and synchronizes with system preference.
 */
(function () {
  // 1. Set initial theme immediately to prevent flashing unstyled content
  const savedTheme = localStorage.getItem('liveshield-theme');
  const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  const initialTheme = savedTheme || (prefersDark ? 'dark' : 'light');
  document.documentElement.setAttribute('data-theme', initialTheme);

  const SUN_SVG = `<svg class="theme-svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"></circle><line x1="12" y1="1" x2="12" y2="3"></line><line x1="12" y1="21" x2="12" y2="23"></line><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line><line x1="1" y1="12" x2="3" y2="12"></line><line x1="21" y1="12" x2="23" y2="12"></line><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line></svg>`;

  const MOON_SVG = `<svg class="theme-svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path></svg>`;

  function createThemeToggle() {
    if (document.getElementById('liveshield-theme-btn')) {
      return;
    }

    const currentTheme = document.documentElement.getAttribute('data-theme') || 'light';
    const isDark = currentTheme === 'dark';

    const btn = document.createElement('button');
    btn.id = 'liveshield-theme-btn';
    btn.type = 'button';
    btn.className = 'theme-toggle-btn';
    btn.title = isDark ? 'Switch to Light Mode' : 'Switch to Dark Mode';
    btn.setAttribute('aria-label', 'Toggle Dark / Light Theme');
    btn.innerHTML = `
      <span class="theme-icon-wrap">${isDark ? SUN_SVG : MOON_SVG}</span>
      <span class="theme-text">${isDark ? 'Light' : 'Dark'}</span>
    `;

    btn.addEventListener('click', function () {
      const active = document.documentElement.getAttribute('data-theme');
      const next = active === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', next);
      localStorage.setItem('liveshield-theme', next);

      const isNowDark = next === 'dark';
      btn.title = isNowDark ? 'Switch to Light Mode' : 'Switch to Dark Mode';
      btn.innerHTML = `
        <span class="theme-icon-wrap">${isNowDark ? SUN_SVG : MOON_SVG}</span>
        <span class="theme-text">${isNowDark ? 'Light' : 'Dark'}</span>
      `;
    });

    // Mount toggle into page with optimal placement
    const topActions = document.querySelector('.top-actions');
    const topbar = document.querySelector('.topbar');
    const sidebarBottom = document.querySelector('.sidebar-bottom');
    const pageHeader = document.querySelector('.page-header');

    if (topActions) {
      topActions.insertBefore(btn, topActions.firstChild);
    } else if (topbar) {
      const container = document.createElement('div');
      container.className = 'top-actions';
      container.appendChild(btn);
      topbar.appendChild(container);
    } else if (sidebarBottom) {
      sidebarBottom.appendChild(btn);
    } else if (pageHeader) {
      pageHeader.appendChild(btn);
    } else {
      btn.classList.add('floating-theme-toggle');
      document.body.appendChild(btn);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', createThemeToggle);
  } else {
    createThemeToggle();
  }
})();
