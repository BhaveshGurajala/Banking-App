import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import axiosInstance from '../api/axiosInstance.js';
import { useAuth } from '../context/useAuth.js';
import './Register.css';

function Register() {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const { login } = useAuth();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    try {
      const response = await axiosInstance.post('/api/auth/register', {
        username,
        email,
        password,
      });
      login(response.data.token, response.data.username);
      navigate('/dashboard');
    } catch (err) {
      console.error('Register failed:', err);
      if (err.response?.status === 409) {
        setError(err.response.data.message || 'Username or email already taken');
      } else {
        setError('Registration failed. Please check your details.');
      }
    }
  };

  return (
    <div className="page">
        <div className="brand-header">
            <div className="brand-logo">B</div>
            <span className="brand-title">Banking App</span>
        </div>
      <div className="card">
        <h1 className="register-title">Create account</h1>
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
            type="email"
            placeholder="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <input
            className="input"
            type="password"
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {error && <p className="error-text">{error}</p>}
          <button className="button" type="submit">Create account</button>
        </form>
        <p className="text-muted auth-footer">
          Already have an account? <Link className="link" to="/login">Log in</Link>
        </p>
      </div>
    </div>
  );
}

export default Register;