import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import axiosInstance from '../api/axiosInstance.js';
import { extractErrorMessage } from '../api/errorUtils.js';
import { useAuth } from '../context/useAuth.js';
import SuccessModal from '../components/SuccessModal.jsx';
import './FormPage.css';

function Transfer() {
  const [accounts, setAccounts] = useState([]);
  const [fromAccountNumber, setFromAccountNumber] = useState('');
  const [toAccountNumber, setToAccountNumber] = useState('');
  const [amount, setAmount] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const { username } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    const fetchAccounts = async () => {
      const response = await axiosInstance.get(`/api/accounts/user/${username}`);
      setAccounts(response.data);
      if (response.data.length > 0) {
        setFromAccountNumber(response.data[0].accountNumber);
      }
    };
    fetchAccounts();
  }, [username]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);

    try {
      await axiosInstance.post('/api/transactions/transfer', {
        fromAccountNumber,
        toAccountNumber,
        amount: parseFloat(amount),
      });
      setSuccess(true);
    } catch (err) {
      console.error('Transfer failed:', err);
      setError(extractErrorMessage(err, 'Transfer failed. Please try again.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="form-page">
      <Link to="/dashboard" className="link form-back">&larr; Back to dashboard</Link>
      <div className="card">
        <div className="form-header">
          <h1>Transfer funds</h1>
          <p className="text-muted form-subtitle">Send money to another account</p>
        </div>
        <form onSubmit={handleSubmit}>
          <select
            className="input"
            value={fromAccountNumber}
            onChange={(e) => setFromAccountNumber(e.target.value)}
          >
            {accounts.map((acc) => (
              <option key={acc.accountNumber} value={acc.accountNumber}>
                From: {acc.accountType} &middot; {acc.accountNumber.slice(0, 8)}... (₹{acc.balance.toFixed(2)})
              </option>
            ))}
          </select>
          <input
            className="input"
            type="text"
            placeholder="Recipient account number"
            value={toAccountNumber}
            onChange={(e) => setToAccountNumber(e.target.value)}
          />
          <input
            className="input"
            type="number"
            step="0.01"
            min="0.01"
            placeholder="Amount"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
          {error && <p className="error-text">{error}</p>}
          <button className="button" type="submit" disabled={submitting || !fromAccountNumber}>
            {submitting ? 'Transferring...' : 'Transfer'}
          </button>
        </form>
      </div>

      {success && (
        <SuccessModal
          title="Transfer successful"
          message={`₹${amount} was sent to ${toAccountNumber.slice(0, 8)}...`}
          onClose={() => navigate('/dashboard')}
        />
      )}      

    </div>
  );
}

export default Transfer;