import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../api/axios";

export default function Dashboard() {
  const [wallet, setWallet] = useState(null);
  const [amount, setAmount] = useState("");
  const [actionError, setActionError] = useState("");
  const [actionSuccess, setActionSuccess] = useState("");
  const navigate = useNavigate();

  const username = localStorage.getItem("username");

  // /api/auth/me already returns walletId, walletNumber, balance, and
  // walletStatus directly on UserResponse — no second API call needed.
  const loadWallet = async () => {
    try {
      const meRes = await api.get("/auth/me");
      const u = meRes.data.data;
      setWallet({
        id: u.walletId,
        walletNumber: u.walletNumber,
        balance: u.balance,
        status: u.walletStatus,
      });
    } catch {
      setActionError("Failed to load wallet.");
    }
  };

  useEffect(() => {
    loadWallet();
  }, []);

  const handleCredit = async () => {
    setActionError("");
    setActionSuccess("");
    if (!amount || Number(amount) <= 0) {
      setActionError("Enter a valid amount.");
      return;
    }
    try {
      await api.post(`/transactions/credit/${wallet.id}`, {
        amount: Number(amount),
        description: "Top-up via dashboard",
      });
      setActionSuccess(`Successfully credited ₹${amount}`);
      setAmount("");
      loadWallet(); // refresh balance
    } catch (err) {
      setActionError(err.response?.data?.message || "Credit failed.");
    }
  };

  const handleLogout = () => {
    localStorage.clear();
    navigate("/login");
  };

  if (!wallet) return <p style={{ textAlign: "center" }}>Loading wallet...</p>;

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <h2>Welcome, {username}</h2>
        <button onClick={handleLogout} style={styles.logoutBtn}>Logout</button>
      </div>

      <div style={styles.balanceCard}>
        <p style={styles.label}>Wallet: {wallet.walletNumber}</p>
        <h1 style={styles.balance}>₹{wallet.balance}</h1>
        <p style={styles.status}>Status: {wallet.status}</p>
      </div>

      <div style={styles.actionCard}>
        <h3>Quick Top-up</h3>
        {actionError && <p style={styles.error}>{actionError}</p>}
        {actionSuccess && <p style={styles.success}>{actionSuccess}</p>}
        <input
          style={styles.input}
          type="number"
          placeholder="Amount"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
        />
        <button style={styles.button} onClick={handleCredit}>Credit Wallet</button>
      </div>

      {/* Placeholders for next-phase pages — History, Beneficiaries, Admin */}
      <div style={styles.navRow}>
        <button style={styles.navBtn} disabled>Transaction History (next)</button>
        <button style={styles.navBtn} disabled>Beneficiaries (next)</button>
      </div>
    </div>
  );
}

const styles = {
  container: { maxWidth: "480px", margin: "2rem auto", padding: "0 1rem" },
  header: { display: "flex", justifyContent: "space-between", alignItems: "center" },
  logoutBtn: { padding: "0.4rem 0.8rem", cursor: "pointer" },
  balanceCard: { background: "#f5f5f5", padding: "1.5rem", borderRadius: "8px", textAlign: "center", margin: "1rem 0" },
  label: { color: "#666", margin: 0 },
  balance: { fontSize: "2.5rem", margin: "0.5rem 0" },
  status: { color: "#888", margin: 0 },
  actionCard: { border: "1px solid #ddd", borderRadius: "8px", padding: "1rem" },
  input: { width: "100%", padding: "0.5rem", boxSizing: "border-box", marginBottom: "0.5rem" },
  button: { width: "100%", padding: "0.6rem", background: "#222", color: "#fff", border: "none", borderRadius: "4px", cursor: "pointer" },
  error: { color: "red", fontSize: "0.9rem" },
  success: { color: "green", fontSize: "0.9rem" },
  navRow: { display: "flex", gap: "0.5rem", marginTop: "1rem" },
  navBtn: { flex: 1, padding: "0.5rem" },
};