import { Link } from 'react-router-dom';
import { useAuth } from '../context/useAuth.js';
import './Navbar.css';

function Navbar() {
  const { username, logout } = useAuth();
  const initials = username ? username.slice(0, 2).toUpperCase() : '';

  return (
    <div className="navbar">
      <Link to="/dashboard" className="navbar-brand">
        <div className="navbar-logo">B</div>
        <span className="navbar-title">Banking App</span>
      </Link>
      <div className="navbar-right">
        <div className="navbar-user">
          <div className="navbar-avatar">{initials}</div>
          <span className="navbar-username">{username}</span>
        </div>
        <button className="navbar-logout" onClick={logout}>
          Log out
        </button>
      </div>
    </div>
  );
}

export default Navbar;