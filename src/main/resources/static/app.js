const apiUrl = '/api/expenses';
const form = document.querySelector('#expense-form');
const list = document.querySelector('#expense-list');
const notice = document.querySelector('#notice');
const count = document.querySelector('#expense-count');
const formError = document.querySelector('#form-error');
const today = new Intl.DateTimeFormat('en-CA').format(new Date());
const currency = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });
document.querySelector('#date').max = today;
document.querySelector('#current-month-label').textContent = new Intl.DateTimeFormat('en-IN', {
  month: 'long', year: 'numeric'
}).format(new Date()).toUpperCase();

async function request(url, options) {
  const response = await fetch(url, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options?.headers }
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.error || `Request failed (${response.status})`);
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
    list.replaceChildren(element('div', 'empty-state', error.message));
    showNotice('Could not load expenses. Is the Spring Boot server running?', true);
  }
}

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
loadExpenses();
