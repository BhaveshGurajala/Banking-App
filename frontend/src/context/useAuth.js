import { useContext } from 'react';
import { AuthContext } from './AuthContextObject.js';

export function useAuth() {
  return useContext(AuthContext);
}