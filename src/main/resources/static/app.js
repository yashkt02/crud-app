const apiUrl = '/api/expenses';
const authUrl = '/gateway/auth/api/auth';
const form = document.querySelector('#expense-form');
const list = document.querySelector('#expense-list');
const notice = document.querySelector('#notice');
const count = document.querySelector('#expense-count');
const formError = document.querySelector('#form-error');
const authForm = document.querySelector('#auth-form');
const authError = document.querySelector('#auth-error');
const expensesApp = document.querySelector('#expenses-app');
const authTokenKey = 'expense-access-token';
const authUsernameKey = 'expense-username';
const lendingApi = '/gateway/summary/api/lend-borrow';
let registering = false;
let outstandingChart;
const today = new Intl.DateTimeFormat('en-CA').format(new Date());
const currency = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });
document.querySelector('#date').max = today;
document.querySelector('#current-month-label').textContent = new Intl.DateTimeFormat('en-IN', {
  month: 'long', year: 'numeric'
}).format(new Date()).toUpperCase();

async function request(url, options) {
  const headers = { 'Content-Type': 'application/json', ...options?.headers };
  const token = sessionStorage.getItem(authTokenKey);
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(url, {
    ...options,
    headers
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    const error = new Error(body.error || body.message || `Request failed (${response.status})`);
    error.status = response.status;
    throw error;
  }
  return response.status === 204 ? null : response.json();
}

function showNotice(message, isError = false) {
  notice.textContent = message;
  notice.classList.toggle('error', isError);
  notice.hidden = false;
  window.setTimeout(() => { notice.hidden = true; }, 3500);
}

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function renderExpenses(expenses) {
  const now = new Date();
  const thisMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  const monthlyTotal = expenses
    .filter((expense) => expense.date.startsWith(thisMonth))
    .reduce((total, expense) => total + Number(expense.amount), 0);
  const allTimeTotal = expenses.reduce((total, expense) => total + Number(expense.amount), 0);
  document.querySelector('#monthly-total').textContent = currency.format(monthlyTotal);
  document.querySelector('#all-time-total').textContent = currency.format(allTimeTotal);
  count.textContent = `${expenses.length} ${expenses.length === 1 ? 'entry' : 'entries'}`;
  list.replaceChildren();
  if (expenses.length === 0) {
    const empty = element('div', 'empty-state');
    empty.append(element('div', 'empty-mark', '₹'));
    empty.append(element('h3', '', 'No expenses recorded'));
    empty.append(element('p', '', 'Add your first expense to see your spending picture.'));
    list.append(empty);
    return;
  }

  for (const expense of expenses) {
    const row = element('article', 'expense-row');
    const icon = element('div', 'expense-icon', expense.category.slice(0, 1).toUpperCase());
    icon.setAttribute('aria-hidden', 'true');
    row.append(icon);

    const main = element('div', 'expense-main');
    main.append(element('h3', '', expense.description));
    const detail = element('div', 'expense-detail');
    detail.append(element('span', 'category-label', expense.category));
    detail.append(element('span', 'expense-date', new Intl.DateTimeFormat('en-IN', {
      day: 'numeric', month: 'short', year: 'numeric'
    }).format(new Date(`${expense.date}T00:00:00`))));
    main.append(detail);
    row.append(main);

    row.append(element('p', 'expense-amount', `−${currency.format(Number(expense.amount))}`));

    const actions = element('div', 'row-actions');
    const editButton = element('button', 'text-button', 'Edit');
    editButton.type = 'button';
    editButton.addEventListener('click', () => openEditForm(expense));
    const deleteButton = element('button', 'text-button delete', 'Delete');
    deleteButton.type = 'button';
    deleteButton.addEventListener('click', () => deleteExpense(expense));
    actions.append(editButton, deleteButton);
    row.append(actions);
    list.append(row);
  }
}

async function loadExpenses() {
  try {
    renderExpenses(await request(apiUrl));
  } catch (error) {
    if (error.status === 401) {
      sessionStorage.removeItem(authTokenKey);
      sessionStorage.removeItem(authUsernameKey);
      setAuthenticated(false);
      authError.textContent = 'Your session expired. Please sign in again.';
      authError.hidden = false;
      return;
    }
    list.replaceChildren(element('div', 'empty-state', error.message));
    showNotice('Could not load expenses. Is the Spring Boot server running?', true);
  }
}

function lendingRequest(url, options) {
  return request(url, { ...options, headers: { Authorization: `Bearer ${sessionStorage.getItem(authTokenKey)}`, ...options?.headers } });
}

async function loadRegisteredPeople() {
  const users = await request('/gateway/auth/api/users');
  let currentUser = sessionStorage.getItem(authUsernameKey);
  if (!currentUser) {
    const tokenPayload = sessionStorage.getItem(authTokenKey).split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    currentUser = JSON.parse(atob(tokenPayload.padEnd(Math.ceil(tokenPayload.length / 4) * 4, '='))).sub;
  }
  const selectableUsers = users.filter((user) => user.username.toLowerCase() !== currentUser.toLowerCase());
  const userSelect = document.querySelector('#lend-recipient');
  const selectedUser = userSelect.value;
  userSelect.replaceChildren(new Option('Choose a user', ''));
  for (const user of selectableUsers) userSelect.add(new Option(user.username, user.username));
  if (selectableUsers.some((user) => user.username === selectedUser)) userSelect.value = selectedUser;
  if (selectableUsers.length === 0) {
    userSelect.options[0].textContent = 'Add another person to get started';
  }
  return selectableUsers;
}

function renderLendingItems(target, items, canDecide = false) {
  target.replaceChildren();
  if (items.length === 0) {
    target.append(element('div', 'empty-state', 'Nothing here yet.'));
    return;
  }
  for (const item of items) {
    const row = element('article', 'lend-request');
    const top = element('div', 'lend-request-top');
    const title = canDecide
      ? `${item.requesterUsername} requests ${item.direction.toLowerCase() === 'lend' ? 'to lend you' : 'to borrow from you'}`
      : `${item.direction === 'LEND' ? 'Lend to' : 'Borrow from'} ${item.direction === 'LEND' ? item.borrowerUsername : item.lenderUsername}`;
    top.append(element('h4', 'lend-request-title', title));
    top.append(element('p', 'lend-request-amount', currency.format(Number(item.amount))));
    row.append(top);
    row.append(element('p', 'lend-request-meta', [item.description, item.status, new Date(item.createdAt).toLocaleDateString('en-IN')].filter(Boolean).join(' · ')));
    if (canDecide) {
      const actions = element('div', 'lend-request-actions');
      for (const decision of ['APPROVE', 'REJECT']) {
        const button = element('button', `text-button${decision === 'REJECT' ? ' delete' : ''}`, decision === 'APPROVE' ? 'Approve' : 'Reject');
        button.type = 'button';
        button.addEventListener('click', () => decideLendingRequest(item.id, decision));
        actions.append(button);
      }
      row.append(actions);
    } else {
      row.append(element('p', 'lend-request-meta', `Recipient: ${item.recipientUsername}`));
    }
    target.append(row);
  }
}

function renderOutstandingChart(people) {
  const chartWrap = document.querySelector('#outstanding-chart-wrap');
  const emptyState = document.querySelector('#outstanding-empty');
  const balances = people.filter((person) => Number(person.outstandingToReceive) > 0 || Number(person.outstandingToPay) > 0);
  if (balances.length === 0) {
    outstandingChart?.destroy();
    outstandingChart = undefined;
    chartWrap.hidden = true;
    emptyState.hidden = false;
    return;
  }

  emptyState.hidden = true;
  chartWrap.hidden = false;
  document.querySelector('#outstanding-chart-frame').style.height = `${Math.max(230, Math.min(600, balances.length * 48 + 86))}px`;
  const labels = balances.map((person) => person.username);
  const formatMoney = (value) => currency.format(Number(value));
  const datasets = [
    {
      label: 'To receive',
      data: balances.map((person) => Number(person.outstandingToReceive)),
      backgroundColor: '#238566',
      borderRadius: 3,
      maxBarThickness: 22
    },
    {
      label: 'To pay',
      data: balances.map((person) => Number(person.outstandingToPay)),
      backgroundColor: '#b84c41',
      borderRadius: 3,
      maxBarThickness: 22
    }
  ];
  if (!outstandingChart) {
    outstandingChart = new Chart(document.querySelector('#outstanding-chart'), {
      type: 'bar',
      data: { labels, datasets },
      options: {
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'nearest', intersect: true },
        plugins: {
          legend: { position: 'bottom', labels: { usePointStyle: true, boxWidth: 8, color: '#596058', font: { family: 'DM Sans', size: 10 } } },
          tooltip: {
            callbacks: {
              title: (items) => items[0]?.label ?? '',
              label: (context) => `${context.dataset.label}: ${formatMoney(context.raw)}`
            }
          }
        },
        scales: {
          x: { beginAtZero: true, grid: { color: '#e7ebe4' }, ticks: { color: '#777c72', callback: (value) => formatMoney(value), font: { family: 'DM Sans', size: 9 } } },
          y: { grid: { display: false }, ticks: { color: '#202c29', font: { family: 'DM Sans', size: 10 } } }
        }
      }
    });
  } else {
    outstandingChart.data.labels = labels;
    outstandingChart.data.datasets = datasets;
    outstandingChart.update('none');
  }
}

function transactionTitle(item, currentUser) {
  return item.lenderUsername.toLowerCase() === currentUser.toLowerCase()
    ? `Lent to ${item.borrowerUsername}`
    : `Borrowed from ${item.lenderUsername}`;
}

function addTransactionFact(target, label, value) {
  const fact = element('div', 'transaction-fact');
  fact.append(element('dt', '', label), element('dd', '', currency.format(Number(value))));
  target.append(fact);
}

function renderTransactionHistory(target, transactions, currentUser) {
  target.replaceChildren();
  if (transactions.length === 0) {
    target.append(element('div', 'empty-state', 'Nothing here yet.'));
    return;
  }
  for (const transaction of transactions) {
    const details = element('details', 'transaction-details');
    const summary = document.createElement('summary');
    const heading = element('span', 'transaction-summary');
    heading.append(element('span', 'transaction-title', transactionTitle(transaction, currentUser)));
    heading.append(element('span', 'transaction-amount', currency.format(Number(transaction.amount))));
    const statusLabel = transaction.repaymentStatus.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
    const status = element('span', `repayment-status${transaction.repaymentStatus === 'FULLY_REPAID' ? ' fully-repaid' : ''}`, statusLabel);
    summary.append(heading, status);
    details.append(summary);

    const body = element('div', 'transaction-detail-body');
    if (transaction.description) body.append(element('p', 'transaction-description', transaction.description));
    const facts = document.createElement('dl');
    facts.className = 'transaction-facts';
    addTransactionFact(facts, 'Original amount', transaction.amount);
    addTransactionFact(facts, 'Repaid', transaction.totalRepaid);
    addTransactionFact(facts, 'Outstanding', transaction.outstandingBalance);
    body.append(facts);

    body.append(element('h5', 'repayment-history-heading', 'Repayment history'));
    const history = element('ul', 'repayment-history');
    if (transaction.repaymentHistory.length === 0) {
      history.append(element('li', '', 'No repayments recorded yet.'));
    } else {
      for (const repayment of transaction.repaymentHistory) {
        const entry = document.createElement('li');
        entry.append(element('span', '', `${repayment.payerUsername} · ${new Date(repayment.createdAt).toLocaleDateString('en-IN')}`));
        entry.append(element('strong', '', currency.format(Number(repayment.amount))));
        history.append(entry);
      }
    }
    body.append(history);

    if (transaction.borrowerUsername.toLowerCase() === currentUser.toLowerCase() && Number(transaction.outstandingBalance) > 0) {
      const repaymentForm = document.createElement('form');
      repaymentForm.className = 'repayment-form';
      const amountLabel = element('label', 'field', 'Repayment amount (₹)');
      const amountInput = document.createElement('input');
      amountInput.type = 'number';
      amountInput.min = '0.01';
      amountInput.step = '0.01';
      amountInput.max = String(transaction.outstandingBalance);
      amountInput.required = true;
      amountInput.inputMode = 'decimal';
      amountLabel.append(amountInput);
      const submit = element('button', 'button button-accent', 'Repay');
      submit.type = 'submit';
      repaymentForm.append(amountLabel, submit);
      const error = element('p', 'repayment-error');
      error.hidden = true;
      repaymentForm.append(error);
      repaymentForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (repaymentForm.dataset.submitting === 'true') return;
        repaymentForm.dataset.submitting = 'true';
        repaymentForm.dataset.idempotencyKey ||= globalThis.crypto?.randomUUID?.()
          || `${Date.now()}-${Math.random().toString(36).slice(2)}`;
        submit.disabled = true;
        submit.textContent = 'Saving…';
        error.hidden = true;
        try {
          await lendingRequest(`${lendingApi}/requests/${transaction.id}/repayments`, {
            method: 'POST',
            headers: { 'Idempotency-Key': repaymentForm.dataset.idempotencyKey },
            body: JSON.stringify({ amount: Number(amountInput.value) })
          });
          const refreshed = await loadLending();
          showNotice(refreshed ? 'Repayment recorded. Balances updated.' : 'Repayment recorded, but the dashboard could not refresh. Reload the page to see the latest balances.', !refreshed);
        } catch (requestError) {
          error.textContent = requestError.message;
          error.hidden = false;
        } finally {
          repaymentForm.dataset.submitting = 'false';
          submit.disabled = false;
          submit.textContent = 'Repay';
        }
      });
      body.append(repaymentForm);
    }
    details.append(body);
    target.append(details);
  }
}

async function loadLending() {
  const peoplePromise = loadRegisteredPeople().catch((error) => {
    showNotice(`Could not load registered users: ${error.message}`, true);
    return [];
  });
  try {
    const overview = await lendingRequest(`${lendingApi}/overview`);
    await peoplePromise;

    const summary = overview.summary;
    document.querySelector('#lent-total').textContent = currency.format(Number(summary.totalLent));
    document.querySelector('#borrowed-total').textContent = currency.format(Number(summary.totalBorrowed));
    document.querySelector('#receive-total').textContent = currency.format(Number(summary.outstandingToReceive));
    document.querySelector('#pay-total').textContent = currency.format(Number(summary.outstandingToPay));
    renderOutstandingChart(overview.outstandingByPerson);
    renderLendingItems(document.querySelector('#received-requests'), overview.pendingReceived, true);
    renderLendingItems(document.querySelector('#sent-requests'), overview.pendingSent);
    renderTransactionHistory(document.querySelector('#lending-history'), overview.confirmedHistory, sessionStorage.getItem(authUsernameKey));
    document.querySelector('#received-count').textContent = overview.pendingReceived.length;
    document.querySelector('#sent-count').textContent = overview.pendingSent.length;
    document.querySelector('#history-count').textContent = overview.confirmedHistory.length;
    return true;
  } catch (error) {
    if (error.status === 401) {
      sessionStorage.removeItem(authTokenKey);
      sessionStorage.removeItem(authUsernameKey);
      setAuthenticated(false);
    }
    showNotice(`Could not load lending data: ${error.message}`, true);
    return false;
  }
}

function setAddPersonForm(open) {
  const personForm = document.querySelector('#add-person-form');
  personForm.hidden = !open;
  document.querySelector('#add-person-toggle').hidden = open;
  if (open) document.querySelector('#person-username').focus();
}

document.querySelector('#add-person-toggle').addEventListener('click', () => setAddPersonForm(true));
document.querySelector('#close-person-form').addEventListener('click', () => setAddPersonForm(false));
document.querySelector('#cancel-person-form').addEventListener('click', () => setAddPersonForm(false));

document.querySelector('#add-person-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const personForm = event.currentTarget;
  const errorNode = document.querySelector('#person-error');
  errorNode.hidden = true;
  try {
    await request(`${authUrl}/register`, {
      method: 'POST',
      body: JSON.stringify({
        username: document.querySelector('#person-username').value.trim(),
        password: document.querySelector('#person-password').value
      })
    });
    const username = document.querySelector('#person-username').value.trim();
    personForm.reset();
    setAddPersonForm(false);
    await loadRegisteredPeople();
    document.querySelector('#lend-recipient').value = username;
    showNotice(`${username} was added. Share their temporary password with them securely.`);
  } catch (error) {
    errorNode.textContent = error.message;
    errorNode.hidden = false;
  }
});

async function decideLendingRequest(id, status) {
  try {
    await lendingRequest(`${lendingApi}/requests/${id}/decision`, {
      method: 'POST', body: JSON.stringify({ status })
    });
    await loadLending();
    showNotice(status === 'APPROVE' ? 'Transaction approved and confirmed.' : 'Request rejected.');
  } catch (error) {
    showNotice(error.message, true);
  }
}

document.querySelector('#lend-borrow-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const lendingForm = event.currentTarget;
  const errorNode = document.querySelector('#lend-error');
  errorNode.hidden = true;
  try {
    await lendingRequest(`${lendingApi}/requests`, {
      method: 'POST',
      body: JSON.stringify({
        recipientUsername: document.querySelector('#lend-recipient').value,
        direction: document.querySelector('#lend-direction').value,
        amount: Number(document.querySelector('#lend-amount').value),
        description: document.querySelector('#lend-description').value.trim() || null
      })
    });
    lendingForm.reset();
    await loadLending();
    showNotice('Request sent. It will count after the recipient approves it.');
  } catch (error) {
    errorNode.textContent = error.message;
    errorNode.hidden = false;
  }
});

function setAuthenticated(authenticated) {
  authForm.hidden = authenticated;
  expensesApp.hidden = !authenticated;
  document.querySelector('#logout-button').hidden = !authenticated;
  if (!authenticated) {
    list.replaceChildren();
    count.textContent = '0 entries';
    document.querySelector('#monthly-total').textContent = currency.format(0);
    document.querySelector('#all-time-total').textContent = currency.format(0);
    document.querySelector('#received-requests').replaceChildren();
    document.querySelector('#sent-requests').replaceChildren();
    document.querySelector('#lending-history').replaceChildren();
    document.querySelector('#lent-total').textContent = currency.format(0);
    document.querySelector('#borrowed-total').textContent = currency.format(0);
    document.querySelector('#receive-total').textContent = currency.format(0);
    document.querySelector('#pay-total').textContent = currency.format(0);
    document.querySelector('#received-count').textContent = '0';
    document.querySelector('#sent-count').textContent = '0';
    document.querySelector('#history-count').textContent = '0';
    renderOutstandingChart([]);
  }
}

authForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  authError.hidden = true;
  const credentials = {
    username: document.querySelector('#auth-username').value.trim(),
    password: document.querySelector('#auth-password').value
  };
  const endpoint = registering ? '/register' : '/login';
  try {
    const session = await request(`${authUrl}${endpoint}`, { method: 'POST', body: JSON.stringify(credentials) });
    sessionStorage.setItem(authTokenKey, session.accessToken);
    sessionStorage.setItem(authUsernameKey, session.username);
    authForm.reset();
    setAuthenticated(true);
    await loadExpenses();
    await loadLending();
    showNotice(registering ? 'Account created.' : 'Signed in.');
  } catch (error) {
    authError.textContent = error.message;
    authError.hidden = false;
  }
});

document.querySelector('#auth-mode-toggle').addEventListener('click', () => {
  registering = !registering;
  document.querySelector('#auth-title').textContent = registering ? 'Create account' : 'Sign in';
  document.querySelector('#auth-submit').textContent = registering ? 'Register' : 'Sign in';
  document.querySelector('#auth-mode-toggle').textContent = registering ? 'Back to sign in' : 'Create account';
  document.querySelector('#auth-password').autocomplete = registering ? 'new-password' : 'current-password';
  authError.hidden = true;
});

document.querySelector('#logout-button').addEventListener('click', () => {
  sessionStorage.removeItem(authTokenKey);
  sessionStorage.removeItem(authUsernameKey);
  setAuthenticated(false);
});

function openNewForm() {
  form.reset();
  document.querySelector('#expense-id').value = '';
  document.querySelector('#date').value = today;
  document.querySelector('#form-eyebrow').textContent = 'NEW ENTRY';
  document.querySelector('#form-title').textContent = 'Expense details';
  formError.hidden = true;
  form.hidden = false;
  document.querySelector('#description').focus();
  form.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

function openEditForm(expense) {
  document.querySelector('#expense-id').value = expense.id;
  document.querySelector('#description').value = expense.description;
  document.querySelector('#category').value = expense.category;
  document.querySelector('#amount').value = expense.amount;
  document.querySelector('#date').value = expense.date;
  document.querySelector('#form-eyebrow').textContent = `EDIT ENTRY / #${expense.id}`;
  document.querySelector('#form-title').textContent = 'Update expense';
  formError.hidden = true;
  form.hidden = false;
  document.querySelector('#description').focus();
  form.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

function closeForm() {
  form.hidden = true;
  form.reset();
  formError.hidden = true;
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  const id = document.querySelector('#expense-id').value;
  const payload = {
    description: document.querySelector('#description').value.trim(),
    category: document.querySelector('#category').value,
    amount: Number(document.querySelector('#amount').value),
    date: document.querySelector('#date').value
  };
  try {
    await request(id ? `${apiUrl}/${id}` : apiUrl, {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify(payload)
    });
    closeForm();
    await loadExpenses();
    showNotice(id ? 'Expense updated.' : 'Expense added.');
  } catch (error) {
    formError.textContent = error.message;
    formError.hidden = false;
  }
});

async function deleteExpense(expense) {
  if (!window.confirm(`Delete “${expense.description}”? This cannot be undone.`)) return;
  try {
    await request(`${apiUrl}/${expense.id}`, { method: 'DELETE' });
    await loadExpenses();
    showNotice('Expense deleted.');
  } catch (error) {
    showNotice(error.message, true);
  }
}

document.querySelector('#new-expense-button').addEventListener('click', openNewForm);
document.querySelector('#cancel-button').addEventListener('click', closeForm);
document.querySelector('#close-form-button').addEventListener('click', closeForm);
if (sessionStorage.getItem(authTokenKey)) {
  setAuthenticated(true);
  loadExpenses();
  loadLending();
} else {
  setAuthenticated(false);
}
