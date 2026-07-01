import { useEffect, useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import api from "../api/axios";

export default function Dashboard() {
  const [wallet, setWallet] = useState(null);
  const [creditAmount, setCreditAmount] = useState("");
  const [debitAmount, setDebitAmount]   = useState("");
  const [actionError,   setActionError]   = useState("");
  const [actionSuccess, setActionSuccess] = useState("");
  const navigate  = useNavigate();
  const username  = localStorage.getItem("username");
  const role      = localStorage.getItem("role");

  // /api/auth/me already includes walletId, walletNumber, balance, walletStatus
  // — no second request needed. Refresh this on every credit/debit to update balance.
  const loadWallet = async () => {
    try {
      const res = await api.get("/auth/me");
      const u   = res.data.data;
      setWallet({
        id:           u.walletId,
        walletNumber: u.walletNumber,
        balance:      u.balance,
        status:       u.walletStatus,
      });
    } catch {
      setActionError("Failed to load wallet.");
    }
  };

  useEffect(() => { loadWallet(); }, []);

  const handleCredit = async () => {
    setActionError(""); setActionSuccess("");
    if (!creditAmount || Number(creditAmount) <= 0) {
      setActionError("Enter a valid amount."); return;
    }
    try {
      await api.post(`/transactions/credit/${wallet.id}`, {
        amount:      Number(creditAmount),
        description: "Top-up via dashboard",
      });
      setActionSuccess(`✓ Credited ₹${creditAmount} successfully`);
      setCreditAmount("");
      loadWallet();
    } catch (err) {
      setActionError(err.response?.data?.message || "Credit failed.");
    }
  };

  const handleDebit = async () => {
    setActionError(""); setActionSuccess("");
    if (!debitAmount || Number(debitAmount) <= 0) {
      setActionError("Enter a valid amount."); return;
    }
    try {
      await api.post(`/transactions/debit/${wallet.id}`, {
        amount:      Number(debitAmount),
        description: "Withdrawal via dashboard",
      });
      setActionSuccess(`✓ Debited ₹${debitAmount} successfully`);
      setDebitAmount("");
      loadWallet();
    } catch (err) {
      // Covers InsufficientFundsException (400) and frozen wallet (403)
      setActionError(err.response?.data?.message || "Debit failed.");
    }
  };

  const handleLogout = () => {
    localStorage.clear();
    navigate("/login");
  };

  if (!wallet) return <p style={{ textAlign: "center", marginTop: "3rem" }}>Loading wallet...</p>;

  return (
    <div style={s.page}>
      {/* ── Top bar ── */}
      <div style={s.topbar}>
        <span style={s.appName}>💰 Digital Wallet</span>
        <div style={s.topRight}>
          <span style={s.greeting}>Hi, {username}</span>
          {role === "ROLE_ADMIN" && (
            <span style={s.adminBadge}>ADMIN</span>
          )}
          <button onClick={handleLogout} style={s.logoutBtn}>Logout</button>
        </div>
      </div>

      {/* ── Balance card ── */}
      <div style={s.balanceCard}>
        <p style={s.walletNum}>{wallet.walletNumber}</p>
        <h1 style={s.balance}>₹ {Number(wallet.balance).toLocaleString("en-IN", { minimumFractionDigits: 2 })}</h1>
        <span style={{ ...s.badge, background: wallet.status === "ACTIVE" ? "#4CAF50" : "#f44336" }}>
          {wallet.status}
        </span>
      </div>

      {/* ── Feedback ── */}
      {actionError   && <p style={s.error}>{actionError}</p>}
      {actionSuccess && <p style={s.success}>{actionSuccess}</p>}

      {/* ── Actions ── */}
      <div style={s.actions}>
        <div style={s.actionCard}>
          <h4 style={s.actionTitle}>Credit</h4>
          <input style={s.input} type="number" placeholder="Amount (₹)"
            value={creditAmount} onChange={e => setCreditAmount(e.target.value)} />
          <button style={{ ...s.btn, background: "#4CAF50" }} onClick={handleCredit}>
            + Add Money
          </button>
        </div>

        <div style={s.actionCard}>
          <h4 style={s.actionTitle}>Debit</h4>
          <input style={s.input} type="number" placeholder="Amount (₹)"
            value={debitAmount} onChange={e => setDebitAmount(e.target.value)} />
          <button style={{ ...s.btn, background: "#f44336" }} onClick={handleDebit}>
            − Withdraw
          </button>
        </div>
      </div>

      {/* ── Navigation ── */}
      <div style={s.navGrid}>
        <Link to="/history"       style={s.navCard}>📋 Transaction History</Link>
        <Link to="/beneficiaries" style={s.navCard}>👥 Beneficiaries</Link>
      </div>
    </div>
  );
}

const s = {
  page:        { maxWidth: "520px", margin: "0 auto", padding: "1rem" },
  topbar:      { display: "flex", justifyContent: "space-between", alignItems: "center",
                 padding: "0.75rem 1rem", background: "#1a1a2e", borderRadius: "8px",
                 marginBottom: "1rem" },
  appName:     { color: "#fff", fontWeight: "bold", fontSize: "1.1rem" },
  topRight:    { display: "flex", alignItems: "center", gap: "0.75rem" },
  greeting:    { color: "#ccc", fontSize: "0.9rem" },
  adminBadge:  { background: "#ff9800", color: "#fff", fontSize: "0.7rem",
                 padding: "2px 6px", borderRadius: "4px" },
  logoutBtn:   { padding: "0.3rem 0.7rem", cursor: "pointer", borderRadius: "4px" },
  balanceCard: { background: "linear-gradient(135deg, #1a1a2e 0%, #16213e 100%)",
                 color: "#fff", padding: "2rem", borderRadius: "12px",
                 textAlign: "center", marginBottom: "1rem" },
  walletNum:   { color: "#aaa", margin: "0 0 0.5rem", fontSize: "0.9rem" },
  balance:     { fontSize: "2.8rem", margin: "0.5rem 0", fontWeight: "bold" },
  badge:       { padding: "3px 10px", borderRadius: "20px", fontSize: "0.8rem" },
  actions:     { display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0.75rem",
                 marginBottom: "1rem" },
  actionCard:  { border: "1px solid #ddd", borderRadius: "8px", padding: "1rem" },
  actionTitle: { margin: "0 0 0.75rem", fontSize: "0.9rem", color: "#555" },
  input:       { width: "100%", padding: "0.5rem", boxSizing: "border-box",
                 marginBottom: "0.5rem", borderRadius: "4px", border: "1px solid #ccc" },
  btn:         { width: "100%", padding: "0.5rem", color: "#fff", border: "none",
                 borderRadius: "4px", cursor: "pointer", fontWeight: "bold" },
  error:       { color: "#d32f2f", background: "#fdecea", padding: "0.6rem",
                 borderRadius: "4px", margin: "0 0 1rem", fontSize: "0.9rem" },
  success:     { color: "#2e7d32", background: "#edf7ed", padding: "0.6rem",
                 borderRadius: "4px", margin: "0 0 1rem", fontSize: "0.9rem" },
  navGrid:     { display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0.75rem" },
  navCard:     { display: "block", padding: "1rem", border: "1px solid #ddd",
                 borderRadius: "8px", textAlign: "center", textDecoration: "none",
                 color: "#333", fontWeight: "500", cursor: "pointer" },
};