import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import axiosInstance from '../api/axiosInstance.js';
import { extractErrorMessage } from '../api/errorUtils.js';
import { useAuth } from '../context/useAuth.js';
import './FormPage.css';

function CreateAccount() {
  const [accountType, setAccountType] = useState('SAVINGS');
  const [initialDeposit, setInitialDeposit] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const { username } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);

    try {
      await axiosInstance.post('/api/accounts', {
        ownerUsername: username,
        accountType,
        initialDeposit: parseFloat(initialDeposit) || 0,
      });
      navigate('/dashboard');
    } catch (err) {
      console.error('Create account failed:', err);
      setError(extractErrorMessage(err, 'Could not create account.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="form-page">
      <Link to="/dashboard" className="link form-back">&larr; Back to dashboard</Link>  
      <div className="card">
        <div className="form-header">
          <h1>Create account</h1>
          <p className="text-muted form-subtitle">Open a new savings or current account</p>
        </div>
        <form onSubmit={handleSubmit}>
          <select
            className="input"
            value={accountType}
            onChange={(e) => setAccountType(e.target.value)}
          >
            <option value="SAVINGS">Savings</option>
            <option value="CURRENT">Current</option>
          </select>
          <input
            className="input"
            type="number"
            step="0.01"
            min="0"
            placeholder="Initial deposit"
            value={initialDeposit}
            onChange={(e) => setInitialDeposit(e.target.value)}
          />
          {error && <p className="error-text">{error}</p>}
          <button className="button" type="submit" disabled={submitting}>
            {submitting ? 'Creating...' : 'Create account'}
          </button>
        </form>
      </div>
    </div>
  );
}

export default CreateAccount;