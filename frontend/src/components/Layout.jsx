import { Outlet } from 'react-router-dom';
import Navbar from './Navbar.jsx';

function Layout() {
  return (
    <>
      <Navbar />
      <Outlet />
    </>
  );
}

export default Layout;