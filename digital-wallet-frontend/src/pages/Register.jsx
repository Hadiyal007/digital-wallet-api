import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import api from "../api/axios";

export default function Register() {
  const [form, setForm] = useState({
    fullName: "", username: "", email: "", password: "",
  });
  // Field-level errors mirror your backend's structured 400 response
  // shape: { errors: { fieldName: "message" } } from Task 4 (@Valid).
  const [fieldErrors, setFieldErrors] = useState({});
  const [generalError, setGeneralError] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleChange = (e) =>
    setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFieldErrors({});
    setGeneralError("");
    setLoading(true);

    try {
      await api.post("/users/register", form);
      navigate("/login");
    } catch (err) {
      const data = err.response?.data;
      if (data?.errors) {
        // @Valid validation failure — show per-field errors
        setFieldErrors(data.errors);
      } else {
        setGeneralError(data?.message || "Registration failed.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.container}>
      <form onSubmit={handleSubmit} style={styles.form}>
        <h2>Create Account</h2>

        {generalError && <p style={styles.error}>{generalError}</p>}

        {["fullName", "username", "email", "password"].map((field) => (
          <div key={field} style={{ marginBottom: "0.8rem" }}>
            <input
              style={styles.input}
              type={field === "password" ? "password" : "text"}
              name={field}
              placeholder={field}
              value={form[field]}
              onChange={handleChange}
              required
            />
            {fieldErrors[field] && (
              <p style={styles.fieldError}>{fieldErrors[field]}</p>
            )}
          </div>
        ))}

        <button style={styles.button} type="submit" disabled={loading}>
          {loading ? "Creating account..." : "Register"}
        </button>

        <p style={{ marginTop: "1rem" }}>
          Already have an account? <Link to="/login">Login</Link>
        </p>
      </form>
    </div>
  );
}

const styles = {
  container: { display: "flex", justifyContent: "center", marginTop: "3rem" },
  form: { width: "340px", padding: "2rem", border: "1px solid #ddd", borderRadius: "8px" },
  input: { width: "100%", padding: "0.6rem", boxSizing: "border-box" },
  button: { width: "100%", padding: "0.6rem", background: "#222", color: "#fff", border: "none", borderRadius: "4px", cursor: "pointer" },
  error: { color: "red", fontSize: "0.9rem" },
  fieldError: { color: "red", fontSize: "0.8rem", margin: "0.2rem 0 0" },
};