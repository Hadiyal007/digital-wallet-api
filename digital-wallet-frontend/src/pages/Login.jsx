import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import api from "../api/axios";

export default function Login() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      // Matches your backend's LoginRequest DTO shape exactly
      const res = await api.post("/auth/login", { username, password });
      const { token, username: returnedUsername, role } = res.data.data;

      // Store JWT + minimal user info — the interceptor reads "token"
      // from here on every future request.
      localStorage.setItem("token", token);
      localStorage.setItem("username", returnedUsername);
      localStorage.setItem("role", role);

      navigate("/dashboard");
    } catch (err) {
      // Your GlobalExceptionHandler returns a generic message for both
      // wrong password AND non-existent user — same message shown here.
      setError(err.response?.data?.message || "Login failed. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.container}>
      <form onSubmit={handleSubmit} style={styles.form}>
        <h2>Digital Wallet — Login</h2>

        {error && <p style={styles.error}>{error}</p>}

        <input
          style={styles.input}
          type="text"
          placeholder="Username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
        />
        <input
          style={styles.input}
          type="password"
          placeholder="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />

        <button style={styles.button} type="submit" disabled={loading}>
          {loading ? "Logging in..." : "Login"}
        </button>

        <p style={{ marginTop: "1rem" }}>
          No account? <Link to="/register">Register here</Link>
        </p>
      </form>
    </div>
  );
}

const styles = {
  container: { display: "flex", justifyContent: "center", marginTop: "5rem" },
  form: { width: "320px", padding: "2rem", border: "1px solid #ddd", borderRadius: "8px" },
  input: { width: "100%", padding: "0.6rem", marginBottom: "0.8rem", boxSizing: "border-box" },
  button: { width: "100%", padding: "0.6rem", background: "#222", color: "#fff", border: "none", borderRadius: "4px", cursor: "pointer" },
  error: { color: "red", fontSize: "0.9rem" },
};