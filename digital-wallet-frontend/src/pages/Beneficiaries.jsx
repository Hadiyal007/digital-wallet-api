import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import api from "../api/axios";

export default function Beneficiaries() {
  const [userId,   setUserId]   = useState(null);
  const [walletId, setWalletId] = useState(null);
  const [list,     setList]     = useState([]);

  // Add-beneficiary form
  const [form, setForm] = useState({ walletNumber: "", beneficiaryName: "", nickname: "" });
  const [fieldErrors,   setFieldErrors]   = useState({});
  const [addError,      setAddError]      = useState("");
  const [addSuccess,    setAddSuccess]    = useState("");

  // Transfer modal
  const [target,         setTarget]         = useState(null); // beneficiary object
  const [transferAmount, setTransferAmount] = useState("");
  const [transferMsg,    setTransferMsg]    = useState("");
  const [transferring,   setTransferring]   = useState(false);

  const loadBeneficiaries = async (uid) => {
    try {
      const res = await api.get(`/beneficiaries/${uid}`);
      setList(res.data.data);
    } catch {
      // list stays empty, user sees "No beneficiaries yet"
    }
  };

  useEffect(() => {
    api.get("/auth/me").then(res => {
      const u = res.data.data;
      setUserId(u.id);
      setWalletId(u.walletId);
      loadBeneficiaries(u.id);
    });
  }, []);

  // ── Add beneficiary ──────────────────────────────────────
  const handleAdd = async (e) => {
    e.preventDefault();
    setFieldErrors({}); setAddError(""); setAddSuccess("");
    try {
      await api.post(`/beneficiaries/${userId}`, form);
      setAddSuccess("Beneficiary added successfully.");
      setForm({ walletNumber: "", beneficiaryName: "", nickname: "" });
      loadBeneficiaries(userId);
    } catch (err) {
      const data = err.response?.data;
      if (data?.errors) setFieldErrors(data.errors);           // @Valid field errors
      else setAddError(data?.message || "Failed to add beneficiary.");
    }
  };

  // ── Delete beneficiary ───────────────────────────────────
  const handleDelete = async (beneficiaryId) => {
    try {
      await api.delete(`/beneficiaries/${userId}/${beneficiaryId}`);
      loadBeneficiaries(userId);
    } catch (err) {
      alert(err.response?.data?.message || "Delete failed.");
    }
  };

  // ── Transfer ─────────────────────────────────────────────
  const handleTransfer = async () => {
    setTransferMsg(""); setTransferring(true);
    try {
      await api.post(`/transactions/transfer/${walletId}`, {
        amount:               Number(transferAmount),
        receiverWalletNumber: target.beneficiaryWalletNumber,
        description:          `Transfer to ${target.beneficiaryName}`,
      });
      setTransferMsg("✓ Transfer successful!");
      setTimeout(() => { setTarget(null); setTransferAmount(""); setTransferMsg(""); }, 1500);
    } catch (err) {
      // Shows InsufficientFundsException, WalletFrozenException, etc.
      setTransferMsg(err.response?.data?.message || "Transfer failed.");
    } finally {
      setTransferring(false);
    }
  };

  const closeModal = () => { setTarget(null); setTransferAmount(""); setTransferMsg(""); };

  return (
    <div style={s.page}>
      <div style={s.header}>
        <h2 style={{ margin: 0 }}>Beneficiaries</h2>
        <Link to="/dashboard" style={s.back}>← Dashboard</Link>
      </div>

      {/* ── Add form ── */}
      <div style={s.card}>
        <h3 style={s.sectionTitle}>Add New Beneficiary</h3>

        {addError   && <p style={s.error}>{addError}</p>}
        {addSuccess && <p style={s.success}>{addSuccess}</p>}

        <form onSubmit={handleAdd}>
          <label style={s.label}>Wallet Number</label>
          <input style={s.input} placeholder="e.g. WALL-USER-001"
            value={form.walletNumber}
            onChange={e => setForm({ ...form, walletNumber: e.target.value })} />
          {fieldErrors.walletNumber && <p style={s.fieldError}>{fieldErrors.walletNumber}</p>}

          <label style={s.label}>Beneficiary Name</label>
          <input style={s.input} placeholder="Full name"
            value={form.beneficiaryName}
            onChange={e => setForm({ ...form, beneficiaryName: e.target.value })} />
          {fieldErrors.beneficiaryName && <p style={s.fieldError}>{fieldErrors.beneficiaryName}</p>}

          <label style={s.label}>Nickname <span style={s.optional}>(optional)</span></label>
          <input style={s.input} placeholder='e.g. "Mom", "Landlord"'
            value={form.nickname}
            onChange={e => setForm({ ...form, nickname: e.target.value })} />

          <button style={s.addBtn} type="submit">+ Add Beneficiary</button>
        </form>
      </div>

      {/* ── Beneficiary list ── */}
      <div style={s.card}>
        <h3 style={s.sectionTitle}>Saved Beneficiaries</h3>
        {list.length === 0 && (
          <p style={{ color: "#888", textAlign: "center" }}>No beneficiaries added yet.</p>
        )}
        {list.map(b => (
          <div key={b.id} style={s.row}>
            <div style={s.rowLeft}>
              <div style={s.avatar}>{(b.nickname || b.beneficiaryName).charAt(0).toUpperCase()}</div>
              <div>
                <div style={s.name}>{b.nickname || b.beneficiaryName}</div>
                <div style={s.sub}>{b.beneficiaryName} · {b.beneficiaryWalletNumber}</div>
              </div>
            </div>
            <div style={s.rowRight}>
              <button style={s.sendBtn} onClick={() => setTarget(b)}>Send Money</button>
              <button style={s.deleteBtn} onClick={() => handleDelete(b.id)}>✕</button>
            </div>
          </div>
        ))}
      </div>

      {/* ── Transfer modal ── */}
      {target && (
        <div style={s.overlay} onClick={closeModal}>
          <div style={s.modal} onClick={e => e.stopPropagation()}>
            <h3 style={{ marginTop: 0 }}>Send to {target.beneficiaryName}</h3>
            <p style={s.sub}>{target.beneficiaryWalletNumber}</p>

            {transferMsg && (
              <p style={transferMsg.startsWith("✓") ? s.success : s.error}>
                {transferMsg}
              </p>
            )}

            <label style={s.label}>Amount (₹)</label>
            <input style={s.input} type="number" placeholder="0.00"
              value={transferAmount}
              onChange={e => setTransferAmount(e.target.value)} />

            <div style={{ display: "flex", gap: "0.5rem", marginTop: "0.5rem" }}>
              <button style={s.addBtn} onClick={handleTransfer} disabled={transferring}>
                {transferring ? "Sending..." : "Confirm Transfer"}
              </button>
              <button style={s.cancelBtn} onClick={closeModal}>Cancel</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

const s = {
  page:       { maxWidth: "560px", margin: "2rem auto", padding: "0 1rem" },
  header:     { display: "flex", justifyContent: "space-between", alignItems: "center",
                marginBottom: "1rem" },
  back:       { textDecoration: "none", color: "#555", fontSize: "0.9rem" },
  card:       { border: "1px solid #e0e0e0", borderRadius: "10px", padding: "1.25rem",
                marginBottom: "1rem" },
  sectionTitle: { margin: "0 0 1rem", fontSize: "1rem", fontWeight: "600", color: "#333" },
  label:      { display: "block", fontSize: "0.85rem", color: "#555", marginBottom: "0.25rem" },
  optional:   { color: "#aaa", fontWeight: "normal" },
  input:      { width: "100%", padding: "0.5rem 0.75rem", boxSizing: "border-box",
                marginBottom: "0.1rem", borderRadius: "6px", border: "1px solid #ccc",
                fontSize: "0.9rem" },
  fieldError: { color: "#d32f2f", fontSize: "0.78rem", margin: "0 0 0.75rem" },
  error:      { color: "#d32f2f", background: "#fdecea", padding: "0.5rem 0.75rem",
                borderRadius: "6px", fontSize: "0.9rem" },
  success:    { color: "#2e7d32", background: "#edf7ed", padding: "0.5rem 0.75rem",
                borderRadius: "6px", fontSize: "0.9rem" },
  addBtn:     { width: "100%", padding: "0.55rem", marginTop: "0.75rem",
                background: "#1a1a2e", color: "#fff", border: "none",
                borderRadius: "6px", cursor: "pointer", fontWeight: "500" },
  cancelBtn:  { flex: 1, padding: "0.55rem", borderRadius: "6px",
                border: "1px solid #ccc", cursor: "pointer", background: "#fff" },
  row:        { display: "flex", justifyContent: "space-between", alignItems: "center",
                padding: "0.75rem 0", borderBottom: "1px solid #f0f0f0" },
  rowLeft:    { display: "flex", alignItems: "center", gap: "0.75rem" },
  rowRight:   { display: "flex", gap: "0.5rem", alignItems: "center" },
  avatar:     { width: "38px", height: "38px", borderRadius: "50%", background: "#1a1a2e",
                color: "#fff", display: "flex", alignItems: "center", justifyContent: "center",
                fontWeight: "bold" },
  name:       { fontWeight: "500", fontSize: "0.95rem" },
  sub:        { fontSize: "0.8rem", color: "#888" },
  sendBtn:    { padding: "0.35rem 0.8rem", background: "#4CAF50", color: "#fff",
                border: "none", borderRadius: "5px", cursor: "pointer", fontSize: "0.85rem" },
  deleteBtn:  { padding: "0.35rem 0.6rem", background: "#fff", color: "#d32f2f",
                border: "1px solid #d32f2f", borderRadius: "5px", cursor: "pointer" },
  overlay:    { position: "fixed", inset: 0, background: "rgba(0,0,0,0.45)",
                display: "flex", alignItems: "center", justifyContent: "center", zIndex: 100 },
  modal:      { background: "#fff", padding: "1.75rem", borderRadius: "12px",
                width: "320px", boxShadow: "0 10px 40px rgba(0,0,0,0.2)" },
};