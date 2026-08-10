import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import axiosInstance from '../api/axiosInstance.js';
import { useAuth } from '../context/useAuth.js';
import './Login.css';

function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const { login } = useAuth();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    try {
      const response = await axiosInstance.post('/api/auth/login', {
        username,
        password,
      });
      login(response.data.token, response.data.username);
      navigate('/dashboard');
    } catch (err) {
      console.error('Login failed:', err);
      setError('Invalid username or password');
    }
  };

  return (
    <div className="page">
        <div className="brand-header">
            <div className="brand-logo">B</div>
            <span className="brand-title">Banking App</span>
        </div>
      <div className="card">
        <h1 className="login-title">Log in</h1>
        <form onSubmit={handleSubmit}>
          <input
            className="input"
            type="text"
            placeholder="Username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
          <input
            className="input"
            type="password"
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {error && <p className="error-text">{error}</p>}
          <button className="button" type="submit">Log in</button>
        </form>
        <p className="text-muted auth-footer">
          Don't have an account? <Link className="link" to="/register">Sign up</Link>
        </p>
      </div>
    </div>
  );
}

export default Login;