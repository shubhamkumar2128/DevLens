/* ============================================================
   DevLens showcase — interactivity
   ============================================================ */
(function () {
  'use strict';

  /* ---------- Theme ---------- */
  const root = document.documentElement;
  const toggle = document.getElementById('themeToggle');
  const stored = localStorage.getItem('devlens-theme');
  const prefersLight = window.matchMedia('(prefers-color-scheme: light)').matches;
  root.setAttribute('data-theme', stored || (prefersLight ? 'light' : 'dark'));

  toggle.addEventListener('click', function () {
    const next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    root.setAttribute('data-theme', next);
    localStorage.setItem('devlens-theme', next);
  });

  /* ---------- Nav: scrolled state + mobile menu ---------- */
  const nav = document.getElementById('nav');
  window.addEventListener('scroll', function () {
    nav.classList.toggle('scrolled', window.scrollY > 10);
  });
  const burger = document.getElementById('navBurger');
  const links = document.querySelector('.nav-links');
  burger.addEventListener('click', () => links.classList.toggle('open'));
  links.querySelectorAll('a').forEach(a => a.addEventListener('click', () => links.classList.remove('open')));

  document.getElementById('year').textContent = new Date().getFullYear();

  /* ---------- Feature cards ----------
     `logic` = short, learning-oriented note on the internal mechanism,
     grounded in the actual devlens-core implementation. */
  const features = [
    { t: '@ExecutionTime', d: 'Measure and log how long a method takes. Warns above a configurable threshold.', c: '⏱',
      logic: 'Wraps the call with <code>System.nanoTime()</code> before and after, then converts the difference to ms. If elapsed &gt; threshold it logs at WARN, otherwise DEBUG.' },
    { t: '@SlowMethod', d: 'Stay quiet until a method exceeds its threshold, then raise a warning. Clean logs.', c: '🐢',
      logic: 'Same timing as @ExecutionTime, but it only emits anything when <code>elapsedMs &gt; threshold</code>. Fast runs produce zero log lines.' },
    { t: '@LogInput', d: 'Log incoming arguments with field-level masking for sensitive data.', c: '↘',
      logic: 'Serializes each argument to a <code>name=value</code> string (truncated at 1024 chars each), then runs the masker over the result <em>before</em> logging — so secrets never hit the log.' },
    { t: '@LogOutput', d: 'Log the return value, with optional length truncation for large payloads.', c: '↗',
      logic: 'Runs only on success. Serializes the return value, then truncates to <code>maxLength</code> (or the global limit) with a <code>...[truncated]</code> marker. <code>void</code> and <code>null</code> are handled explicitly.' },
    { t: '@LogException', d: 'Capture exceptions with full context and timing — without swallowing them.', c: '⚠',
      logic: 'The engine catches the throwable into a variable, logs type + message + elapsed + thread (+ optional stack trace) at ERROR, then <strong>re-throws the original</strong> at the end. Your control flow is unchanged.' },
    { t: '@Retry', d: 'Automatic retries with delay, exponential backoff and exception filtering.', c: '↻',
      logic: 'A loop of up to <code>maxAttempts</code>. On failure it checks <code>noRetryOn</code> first (stop), then <code>retryOn</code>. Backoff delay is <code>delay × 2^(attempt-1)</code>, capped at 30s. <code>InterruptedException</code> is never retried.' },
    { t: '@CorrelationId', d: 'Generate one ID and propagate it across every nested call in the chain.', c: '🔗',
      logic: 'Uses a <code>ThreadLocal</code> + depth counter. The outermost call generates an 8-char UUID and puts it in SLF4J <code>MDC</code>; nested calls just increment depth and reuse it. Cleared only when depth returns to 0.' },
    { t: '@ExternalCall', d: 'Track external dependencies with timing and success / failure status.', c: '☁',
      logic: 'Times the call and records status as <code>SUCCESS</code> or <code>FAILED</code> based on whether an exception was thrown. Logs at WARN when over threshold, ERROR on failure.' },
    { t: '@MemoryUsage', d: 'Report JVM heap before and after a method, with the delta.', c: '📊',
      logic: 'Reads <code>Runtime.totalMemory() - freeMemory()</code> before and after. With <code>forceGC=true</code> it calls <code>System.gc()</code> first for a cleaner baseline, then logs before/after/delta.' },
    { t: '@ThreadInfo', d: 'Log thread name, id and state at entry, exit, or both.', c: '🧵',
      logic: 'Reads <code>Thread.currentThread()</code> at the configured phase. <code>logAt</code> decides whether it fires at ENTRY (pre), EXIT (post), or BOTH — handy for spotting async thread switches.' },
    { t: '@DevTrace', d: 'All-in-one structured trace: input, output, timing, thread and status.', c: '🔍',
      logic: 'Takes a dedicated path in the engine: captures input, runs the method (through @Retry if present), then builds one <code>DevTraceRecord</code> with input, output, timing, thread and status — emitted as a single block instead of many lines.' },
    { t: 'Fail-safe engine', d: 'Every step is isolated so DevLens can never break your method.', c: '🛡',
      logic: 'Each annotation step is wrapped in its own try/catch. If serialization or masking fails, it calls <code>logInternalError</code> at WARN and moves on — the method still runs and returns normally.' }
  ];
  const fg = document.getElementById('featureGrid');
  fg.innerHTML = features.map((f, i) => `
    <article class="feature-card" data-i="${i}" tabindex="0" role="button" aria-expanded="false" aria-label="${f.t} — click to see how it works">
      <div class="f-icon">${f.c}</div>
      <h3><code>${f.t}</code></h3>
      <p>${f.d}</p>
      <span class="f-hint">Click to see how it works ↓</span>
      <div class="f-logic"><span class="f-logic-label">Under the hood</span><p>${f.logic}</p></div>
    </article>`).join('');

  // spotlight hover
  fg.querySelectorAll('.feature-card').forEach(card => {
    card.addEventListener('mousemove', e => {
      const r = card.getBoundingClientRect();
      card.style.setProperty('--mx', (e.clientX - r.left) + 'px');
      card.style.setProperty('--my', (e.clientY - r.top) + 'px');
    });
    const flip = () => {
      const open = card.classList.toggle('open');
      card.setAttribute('aria-expanded', open ? 'true' : 'false');
    };
    card.addEventListener('click', flip);
    card.addEventListener('keydown', e => {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); flip(); }
    });
  });

  /* ---------- Annotation explorer ---------- */
  const anns = [
    { name: '@ExecutionTime', file: 'ReportService.java', desc: 'Measures and logs method execution time. Logs at WARN when it exceeds the threshold, DEBUG otherwise.',
      use: ['API endpoints & response times', 'Slow database queries', 'Batch job durations'],
      code: `<span class="tk-an">@ExecutionTime</span>(threshold = <span class="tk-nu">500</span>)
<span class="tk-kw">public</span> <span class="tk-ty">Report</span> <span class="tk-fn">generateMonthlyReport</span>(<span class="tk-ty">int</span> month) {
    <span class="tk-kw">return</span> reportEngine.<span class="tk-fn">build</span>(month);
}
<span class="tk-cm">// [DevLens] ExecutionTime method=generateMonthlyReport executionTime=1200ms</span>` },

    { name: '@SlowMethod', file: 'InventoryService.java', desc: 'Logs a warning only when execution exceeds the threshold. No output for fast runs, so your logs stay clean.',
      use: ['Background & scheduled jobs', 'Catching occasional spikes', 'Connection-pool exhaustion'],
      code: `<span class="tk-an">@SlowMethod</span>(threshold = <span class="tk-nu">1000</span>)
<span class="tk-kw">public</span> <span class="tk-ty">void</span> <span class="tk-fn">syncExternalInventory</span>() {
    inventoryClient.<span class="tk-fn">fetchAndUpdate</span>();
}
<span class="tk-cm">// WARN [DevLens][SLOW] method=syncExternalInventory executionTime=2341ms</span>` },

    { name: '@LogInput', file: 'AuthController.java', desc: 'Logs method arguments at entry. Supports per-method masking on top of globally masked fields.',
      use: ['Debugging & audit logging', 'Reproducing bugs from inputs', 'Tracking request payloads'],
      code: `<span class="tk-an">@LogInput</span>(maskFields = {<span class="tk-st">"password"</span>, <span class="tk-st">"ssn"</span>})
<span class="tk-kw">public</span> <span class="tk-ty">User</span> <span class="tk-fn">register</span>(<span class="tk-ty">RegistrationRequest</span> request) {
    <span class="tk-kw">return</span> userService.<span class="tk-fn">create</span>(request);
}
<span class="tk-cm">// args=[{username=john, password=***, ssn=***}]</span>` },

    { name: '@LogOutput', file: 'ProductService.java', desc: 'Logs the return value after a successful call. Optional maxLength truncates large payloads.',
      use: ['Verifying transformations', 'Validating cached values', 'API response logging'],
      code: `<span class="tk-an">@LogOutput</span>(maxLength = <span class="tk-nu">200</span>)
<span class="tk-kw">public</span> <span class="tk-ty">List</span>&lt;<span class="tk-ty">Product</span>&gt; <span class="tk-fn">searchProducts</span>(<span class="tk-ty">String</span> query) {
    <span class="tk-kw">return</span> productRepository.<span class="tk-fn">search</span>(query);
}
<span class="tk-cm">// result=[Product{id=1...}, Product{id=2...}] ...[truncated]</span>` },

    { name: '@LogException', file: 'PaymentService.java', desc: 'Logs detailed exception info — type, message, timing, thread — without swallowing the exception.',
      use: ['Service-layer methods', 'Integration points', 'Critical business logic'],
      code: `<span class="tk-an">@LogException</span>(includeStackTrace = <span class="tk-nu">true</span>)
<span class="tk-kw">public</span> <span class="tk-ty">void</span> <span class="tk-fn">processPayment</span>(<span class="tk-ty">PaymentRequest</span> request) {
    paymentGateway.<span class="tk-fn">charge</span>(request);
}
<span class="tk-cm">// ERROR [DevLens] Exception exception=SocketTimeoutException executionTime=5002ms</span>` },

    { name: '@Retry', file: 'ConfigClient.java', desc: 'Retries on failure with configurable attempts, delay, exponential backoff and exception filtering.',
      use: ['External HTTP calls', 'Message queue publishing', 'Transient DB failures'],
      code: `<span class="tk-an">@Retry</span>(maxAttempts = <span class="tk-nu">3</span>, delay = <span class="tk-nu">500</span>, exponentialBackoff = <span class="tk-nu">true</span>,
       retryOn = {<span class="tk-ty">SocketTimeoutException</span>.<span class="tk-kw">class</span>})
<span class="tk-kw">public</span> <span class="tk-ty">ApiResponse</span> <span class="tk-fn">callExternalApi</span>(<span class="tk-ty">String</span> endpoint) {
    <span class="tk-kw">return</span> restTemplate.<span class="tk-fn">getForObject</span>(endpoint, <span class="tk-ty">ApiResponse</span>.<span class="tk-kw">class</span>);
}` },

    { name: '@CorrelationId', file: 'OrderService.java', desc: 'Generates a unique 8-char ID and propagates it across all nested calls on the same thread — also set in SLF4J MDC.',
      use: ['API controllers', 'Event handlers', 'Tracing multi-step flows'],
      code: `<span class="tk-an">@CorrelationId</span>
<span class="tk-an">@LogInput</span> <span class="tk-an">@ExecutionTime</span>
<span class="tk-kw">public</span> <span class="tk-ty">Order</span> <span class="tk-fn">processOrder</span>(<span class="tk-ty">OrderRequest</span> request) {
    <span class="tk-fn">validateOrder</span>(request);
    <span class="tk-fn">chargePayment</span>(request);
    <span class="tk-kw">return</span> <span class="tk-fn">createOrder</span>(request);
}
<span class="tk-cm">// every line shares correlationId=a3f2b91c</span>` },

    { name: '@ExternalCall', file: 'InventoryClient.java', desc: 'Logs external dependency calls — service name, response time and SUCCESS / FAILED status.',
      use: ['REST / SOAP / GraphQL calls', 'DB treated as dependency', 'Cache & broker operations'],
      code: `<span class="tk-an">@ExternalCall</span>(value = <span class="tk-st">"inventory-service"</span>, threshold = <span class="tk-nu">500</span>)
<span class="tk-kw">public</span> <span class="tk-ty">StockLevel</span> <span class="tk-fn">checkStock</span>(<span class="tk-ty">String</span> sku) {
    <span class="tk-kw">return</span> inventoryClient.<span class="tk-fn">getStock</span>(sku);
}
<span class="tk-cm">// [DevLens] ExternalCall service=inventory-service executionTime=89ms status=SUCCESS</span>` },

    { name: '@MemoryUsage', file: 'DataLoader.java', desc: 'Reports JVM heap usage before and after a method, showing the delta. Optionally forces GC first.',
      use: ['Large data processing', 'Cache loading', 'Memory-leak investigation'],
      code: `<span class="tk-an">@MemoryUsage</span>(forceGC = <span class="tk-nu">true</span>)
<span class="tk-kw">public</span> <span class="tk-ty">void</span> <span class="tk-fn">loadLargeDataset</span>(<span class="tk-ty">String</span> path) {
    dataset = fileReader.<span class="tk-fn">readAll</span>(path);
}
<span class="tk-cm">// before=31457280B after=524288000B delta=+492830720B</span>` },

    { name: '@ThreadInfo', file: 'EmailService.java', desc: 'Logs thread name, id and state at entry, exit or both. Essential for concurrency debugging.',
      use: ['Async method dispatch', 'Thread-pool starvation', 'Detecting thread switches'],
      code: `<span class="tk-an">@Async</span>(<span class="tk-st">"emailPool"</span>)
<span class="tk-an">@ThreadInfo</span>(logAt = <span class="tk-ty">LogAt</span>.<span class="tk-nu">BOTH</span>)
<span class="tk-kw">public</span> <span class="tk-ty">void</span> <span class="tk-fn">sendEmail</span>(<span class="tk-ty">String</span> to, <span class="tk-ty">String</span> body) {
    emailClient.<span class="tk-fn">send</span>(to, body);
}
<span class="tk-cm">// thread=emailPool-1-thread-3 state=RUNNABLE phase=ENTRY</span>` },

    { name: '@DevTrace', file: 'AuthService.java', desc: 'All-in-one structured trace combining input, output, timing, thread info, correlation and status.',
      use: ['Critical business methods', 'API entry points', 'Deep debugging sessions'],
      code: `<span class="tk-an">@DevTrace</span>(maskFields = {<span class="tk-st">"password"</span>}, maxOutputLength = <span class="tk-nu">1000</span>)
<span class="tk-kw">public</span> <span class="tk-ty">User</span> <span class="tk-fn">authenticate</span>(<span class="tk-ty">String</span> username, <span class="tk-ty">String</span> password) {
    <span class="tk-kw">return</span> authService.<span class="tk-fn">login</span>(username, password);
}
<span class="tk-cm">// ═══ DevTrace ═══ elapsed:333ms status:SUCCESS input:[password=***]</span>` }
  ];

  const tabs = document.getElementById('annTabs');
  const elName = document.getElementById('annName');
  const elDesc = document.getElementById('annDesc');
  const elUse = document.getElementById('annUse');
  const elCode = document.getElementById('annCode');
  const elFile = document.getElementById('annFile');

  tabs.innerHTML = anns.map((a, i) =>
    `<button class="ann-tab${i === 0 ? ' active' : ''}" role="tab" data-i="${i}">${a.name}</button>`).join('');

  function selectAnn(i) {
    const a = anns[i];
    elName.textContent = a.name;
    elDesc.textContent = a.desc;
    elFile.textContent = a.file;
    elUse.innerHTML = a.use.map(u => `<li>${u}</li>`).join('');
    elCode.innerHTML = a.code;
    tabs.querySelectorAll('.ann-tab').forEach((t, j) => t.classList.toggle('active', i === j));
  }
  tabs.addEventListener('click', e => {
    const b = e.target.closest('.ann-tab');
    if (b) selectAnn(+b.dataset.i);
  });
  selectAnn(0);

  /* ---------- Config format toggle (YAML / Properties) ---------- */
  const fmtTabs = document.querySelectorAll('.fmt-tab');
  fmtTabs.forEach(tab => {
    tab.addEventListener('click', () => {
      const fmt = tab.dataset.fmt;
      fmtTabs.forEach(t => {
        const on = t === tab;
        t.classList.toggle('active', on);
        t.setAttribute('aria-selected', on ? 'true' : 'false');
      });
      document.querySelectorAll('.cfg-view').forEach(v => { v.hidden = v.dataset.fmt !== fmt; });
    });
  });
  function activeCfgEl() { return document.querySelector('.cfg-view:not([hidden])'); }

  /* ---------- Copy to clipboard ---------- */
  function textFromEl(el) { return el ? el.innerText.replace(/\u00a0/g, ' ') : ''; }

  document.querySelectorAll('.copy-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      let text;
      if (btn.id === 'annCopy') {
        text = textFromEl(elCode);
      } else if (btn.dataset.copyActive === 'cfg') {
        text = textFromEl(activeCfgEl());
      } else if (btn.dataset.copy) {
        text = textFromEl(document.getElementById(btn.dataset.copy));
      }
      if (!text) return;
      navigator.clipboard.writeText(text).then(() => {
        const old = btn.textContent;
        btn.textContent = 'Copied!';
        btn.classList.add('done');
        setTimeout(() => { btn.textContent = old; btn.classList.remove('done'); }, 1400);
      }).catch(() => {});
    });
  });

  /* ---------- Cross-browser JAR download ----------
     The `download` attribute is ignored for cross-origin URLs (Chrome is
     lenient, Firefox is not). Fetch the file as a blob and save it so it
     downloads consistently. Falls back to opening the raw URL if fetch fails. */
  const jarBtn = document.getElementById('jarDownload');
  if (jarBtn) {
    jarBtn.addEventListener('click', function (e) {
      e.preventDefault();
      const url = jarBtn.getAttribute('href');
      const filename = jarBtn.dataset.filename || 'download.jar';
      const label = jarBtn.querySelector('.jar-label');
      const original = label ? label.textContent : '';
      if (label) label.textContent = 'Downloading…';
      jarBtn.classList.add('loading');

      fetch(url)
        .then(res => { if (!res.ok) throw new Error('HTTP ' + res.status); return res.blob(); })
        .then(blob => {
          const objUrl = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = objUrl;
          a.download = filename;
          document.body.appendChild(a);
          a.click();
          a.remove();
          URL.revokeObjectURL(objUrl);
        })
        .catch(() => {
          // Fallback: let the browser handle the raw URL directly
          window.open(url, '_blank', 'noopener');
        })
        .finally(() => {
          if (label) label.textContent = original;
          jarBtn.classList.remove('loading');
        });
    });
  }

  /* ---------- Scroll reveal ---------- */
  const io = new IntersectionObserver(entries => {
    entries.forEach(e => { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } });
  }, { threshold: 0.12 });
  document.querySelectorAll('.reveal').forEach(el => io.observe(el));

  /* ---------- Animated counters ---------- */
  const counters = document.querySelectorAll('[data-count]');
  const cio = new IntersectionObserver(entries => {
    entries.forEach(e => {
      if (!e.isIntersecting) return;
      const el = e.target, target = +el.dataset.count;
      cio.unobserve(el);
      if (target === 0) { el.textContent = '0'; return; }
      const dur = 1100, start = performance.now();
      function tick(now) {
        const p = Math.min((now - start) / dur, 1);
        const eased = 1 - Math.pow(1 - p, 3);
        el.textContent = Math.round(target * eased).toString();
        if (p < 1) requestAnimationFrame(tick);
      }
      requestAnimationFrame(tick);
    });
  }, { threshold: 0.5 });
  counters.forEach(c => cio.observe(c));
})();
