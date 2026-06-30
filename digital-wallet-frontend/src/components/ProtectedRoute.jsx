import { Navigate } from "react-router-dom";

// Wraps any page that requires login. If there's no token in
// localStorage, redirect to /login instead of rendering the page.
// This is the client-side mirror of your backend's
// .anyRequest().authenticated() rule in SecurityConfig.
export default function ProtectedRoute({ children }) {
  const token = localStorage.getItem("token");

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  return children;
}