import axios from "axios";

// Base URL points to your local Spring Boot server.
// Change this if your backend runs on a different port.
const api = axios.create({
  baseURL: "http://localhost:8080/api",
});

// REQUEST interceptor: runs before every single request.
// Attaches the JWT (if present) so you never have to manually add
// the Authorization header in every component.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// RESPONSE interceptor: runs after every response.
// If the backend returns 401 (token missing/expired/invalid), we
// clear the stale token and force the user back to login — this is
// the global handling Task 2 (JWT) promised on the frontend side.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem("token");
      localStorage.removeItem("username");
      localStorage.removeItem("role");
      window.location.href = "/login";
    }
    return Promise.reject(error);
  }
);

export default api;