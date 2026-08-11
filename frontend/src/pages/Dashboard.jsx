import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import axiosInstance from '../api/axiosInstance.js';
import { useAuth } from '../context/useAuth.js';
import './Dashboard.css';
import SkeletonList from '../components/SkeletonList.jsx';


function Dashboard() {
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const { username } = useAuth();

  useEffect(() => {
    const fetchAccounts = async () => {
      try {
        const response = await axiosInstance.get(`/api/accounts/user/${username}`);
        setAccounts(response.data);
      } catch (err) {
        console.error('Failed to load accounts:', err);
        setError('Could not load your accounts.');
      } finally {
        setLoading(false);
      }
    };

    fetchAccounts();
  }, [username]);

  return (
    <div className="dashboard">
      <div className="section-header">
        <h1 className="dashboard-greeting">Your accounts</h1>
        <Link to="/history" className="link">Transaction history &rarr;</Link>
      </div>
      {loading && <SkeletonList rows={2} />}
      {error && <p className="error-text">{error}</p>}

      {!loading && !error && (
        <>
          <div className="account-grid">
            {accounts.map((account) => (
              <div key={account.accountNumber} className="card account-card">
                <p className="text-muted account-meta">
                  {account.accountType} &middot; {account.accountNumber.slice(0, 8)}...
                </p>
                <h2>₹{account.balance.toFixed(2)}</h2>
              </div>
            ))}
          </div>

          {accounts.length === 0 && (
            <p className="text-muted">You don't have any accounts yet.</p>
          )}

          <div className="action-bar">
            <Link className="action-link button" to="/create-account">Create account</Link>
            <Link className="action-link button-secondary" to="/deposit">Deposit</Link>
            <Link className="action-link button-secondary" to="/withdraw">Withdraw</Link>
            <Link className="action-link button-secondary" to="/transfer">Transfer</Link>
          </div>
        </>
      )}
    </div>
  );
}

export default Dashboard;