const API_URL = '';

// DOM Elements
const habitsListEl = document.getElementById('habits-list');
const addHabitForm = document.getElementById('add-habit-form');
const editModal = document.getElementById('edit-modal');
const deleteModal = document.getElementById('delete-modal');
const editHabitForm = document.getElementById('edit-habit-form');
const showInactiveCheckbox = document.getElementById('show-inactive');

// Load habits on page load
document.addEventListener('DOMContentLoaded', () => {
    loadHabits();
});

// Event Listeners
addHabitForm.addEventListener('submit', handleAddHabit);
editHabitForm.addEventListener('submit', handleEditHabit);
showInactiveCheckbox.addEventListener('change', loadHabits);

document.querySelector('.close').addEventListener('click', () => closeModal(editModal));
document.getElementById('cancel-edit').addEventListener('click', () => closeModal(editModal));
document.getElementById('cancel-delete').addEventListener('click', () => closeModal(deleteModal));
document.getElementById('confirm-delete').addEventListener('click', handleDeleteHabit);

// Close modals when clicking outside
window.addEventListener('click', (e) => {
    if (e.target === editModal) closeModal(editModal);
    if (e.target === deleteModal) closeModal(deleteModal);
});

// API Functions
async function loadHabits() {
    try {
        const showInactive = showInactiveCheckbox.checked;
        let url = `${API_URL}/habits`;
        if (!showInactive) {
            url += '?is_active=true';
        }

        const response = await fetch(url);
        const habits = await response.json();
        renderHabits(habits);
    } catch (error) {
        console.error('Error loading habits:', error);
        habitsListEl.innerHTML = '<p class="empty-state">Error loading habits. Please try again.</p>';
    }
}

async function handleAddHabit(e) {
    e.preventDefault();

    const name = document.getElementById('habit-name').value.trim();
    const frequency = document.getElementById('habit-frequency').value;

    try {
        const response = await fetch(`${API_URL}/habits`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, frequency })
        });

        if (response.ok) {
            showToast('Habit created successfully!', 'success');
            addHabitForm.reset();
            loadHabits();
        } else {
            const error = await response.json();
            showToast(error.detail || 'Error creating habit', 'error');
        }
    } catch (error) {
        console.error('Error creating habit:', error);
        showToast('Error creating habit', 'error');
    }
}

async function handleEditHabit(e) {
    e.preventDefault();

    const id = document.getElementById('edit-habit-id').value;
    const name = document.getElementById('edit-habit-name').value.trim();
    const frequency = document.getElementById('edit-habit-frequency').value;
    const isActive = document.getElementById('edit-habit-active').checked;

    try {
        const response = await fetch(`${API_URL}/habits/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, frequency, is_active: isActive })
        });

        if (response.ok) {
            showToast('Habit updated successfully!', 'success');
            closeModal(editModal);
            loadHabits();
        } else {
            const error = await response.json();
            showToast(error.detail || 'Error updating habit', 'error');
        }
    } catch (error) {
        console.error('Error updating habit:', error);
        showToast('Error updating habit', 'error');
    }
}

async function handleDeleteHabit() {
    const id = document.getElementById('delete-habit-id').value;

    try {
        const response = await fetch(`${API_URL}/habits/${id}`, {
            method: 'DELETE'
        });

        if (response.ok) {
            showToast('Habit deleted successfully!', 'success');
            closeModal(deleteModal);
            loadHabits();
        } else {
            const error = await response.json();
            showToast(error.detail || 'Error deleting habit', 'error');
        }
    } catch (error) {
        console.error('Error deleting habit:', error);
        showToast('Error deleting habit', 'error');
    }
}

// UI Functions
function renderHabits(habits) {
    if (habits.length === 0) {
        habitsListEl.innerHTML = '<p class="empty-state">No habits yet. Create your first habit above!</p>';
        return;
    }

    habitsListEl.innerHTML = habits.map(habit => `
        <div class="habit-card ${habit.is_active ? '' : 'inactive'}">
            <div class="habit-info">
                <h3>${escapeHtml(habit.name)}</h3>
                <div class="habit-meta">
                    <span>Frequency: ${habit.frequency}</span>
                    <span class="status-badge ${habit.is_active ? 'active' : 'inactive'}">
                        ${habit.is_active ? 'Active' : 'Inactive'}
                    </span>
                </div>
            </div>
            <div class="habit-actions">
                <button class="btn btn-secondary" onclick="openEditModal(${habit.id}, '${escapeHtml(habit.name)}', '${habit.frequency}', ${habit.is_active})">Edit</button>
                <button class="btn btn-danger" onclick="openDeleteModal(${habit.id}, '${escapeHtml(habit.name)}')">Delete</button>
            </div>
        </div>
    `).join('');
}

function openEditModal(id, name, frequency, isActive) {
    document.getElementById('edit-habit-id').value = id;
    document.getElementById('edit-habit-name').value = name;
    document.getElementById('edit-habit-frequency').value = frequency;
    document.getElementById('edit-habit-active').checked = isActive;
    editModal.style.display = 'block';
}

function openDeleteModal(id, name) {
    document.getElementById('delete-habit-id').value = id;
    document.getElementById('delete-habit-name').textContent = name;
    deleteModal.style.display = 'block';
}

function closeModal(modal) {
    modal.style.display = 'none';
}

function showToast(message, type) {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    document.body.appendChild(toast);

    setTimeout(() => {
        toast.remove();
    }, 3000);
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
