import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import axiosInstance from '../api/axiosInstance.js';
import { useAuth } from '../context/useAuth.js';
import SkeletonList from '../components/SkeletonList.jsx';
import './TransactionHistory.css';

function TransactionHistory() {
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const { username } = useAuth();

  useEffect(() => {
    const fetchHistory = async () => {
      try {
        const accountsRes = await axiosInstance.get(`/api/accounts/user/${username}`);
        const accounts = accountsRes.data;

        const historyPerAccount = await Promise.all(
          accounts.map((acc) =>
            axiosInstance.get(`/api/transactions/account/${acc.accountNumber}`)
          )
        );

        const merged = historyPerAccount.flatMap((res) => res.data);
        merged.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
        setTransactions(merged);
      } catch (err) {
        console.error('Failed to load transaction history:', err);
        setError('Could not load transaction history.');
      } finally {
        setLoading(false);
      }
    };

    fetchHistory();
  }, [username]);

  const describeTransaction = (txn) => {
    switch (txn.type) {
      case 'DEPOSIT':
        return { label: 'Deposit', sign: '+', account: txn.toAccountNumber };
      case 'WITHDRAWAL':
        return { label: 'Withdrawal', sign: '-', account: txn.fromAccountNumber };
      case 'TRANSFER':
        return { label: `Transfer to ${txn.toAccountNumber?.slice(0, 8)}...`, sign: '-', account: txn.fromAccountNumber };
      default:
        return { label: txn.type, sign: '', account: '' };
    }
  };

  return (
    <div className="history-page">
      <Link to="/dashboard" className="link form-back">&larr; Back to dashboard</Link>
      <h1 className="dashboard-greeting">Transaction history</h1>

      {loading && <SkeletonList rows={5} />}
      {error && <p className="error-text">{error}</p>}

      {!loading && !error && (
        <div className="card history-list">
          {transactions.length === 0 && (
            <p className="text-muted">No transactions yet.</p>
          )}
          {transactions.map((txn) => {
            const { label, sign } = describeTransaction(txn);
            return (
              <div key={txn.id} className="history-row">
                <div>
                  <p className="history-label">{label}</p>
                  <p className="text-muted history-date">
                    {new Date(txn.timestamp).toLocaleString()}
                  </p>
                </div>
                <span className={`history-amount ${sign === '+' ? 'amount-positive' : 'amount-negative'}`}>
                  {sign}₹{txn.amount.toFixed(2)}
                </span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default TransactionHistory;