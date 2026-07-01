import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import api from "../api/axios";

// Colour-codes transaction type label for visual clarity
const typeStyle = (type) => {
  if (type === "CREDIT")   return { color: "#2e7d32", fontWeight: "bold" };
  if (type === "DEBIT")    return { color: "#c62828", fontWeight: "bold" };
  if (type === "TRANSFER") return { color: "#1565c0", fontWeight: "bold" };
  return {};
};

export default function History() {
  const [walletId,  setWalletId]  = useState(null);
  const [pageData,  setPageData]  = useState(null);
  const [page,      setPage]      = useState(0);
  const [error,     setError]     = useState("");

  // Step 1: resolve walletId once from /auth/me
  useEffect(() => {
    api.get("/auth/me")
      .then(res => setWalletId(res.data.data.walletId))
      .catch(() => setError("Failed to load user."));
  }, []);

  // Step 2: fetch paginated history whenever walletId or page changes
  useEffect(() => {
    if (!walletId) return;
    setError("");
    api.get(`/transactions/history/${walletId}`, { params: { page, size: 10 } })
      .then(res => setPageData(res.data.data))
      .catch(err => setError(err.response?.data?.message || "Failed to load history."));
  }, [walletId, page]);

  return (
    <div style={s.page}>
      <div style={s.header}>
        <h2 style={{ margin: 0 }}>Transaction History</h2>
        <Link to="/dashboard" style={s.back}>← Dashboard</Link>
      </div>

      {error && <p style={s.error}>{error}</p>}

      {!pageData
        ? <p>Loading...</p>
        : <>
            <div style={s.meta}>
              {pageData.totalElements} total transaction{pageData.totalElements !== 1 ? "s" : ""}
            </div>

            <table style={s.table}>
              <thead>
                <tr style={s.theadRow}>
                  <th style={s.th}>Date & Time</th>
                  <th style={s.th}>Type</th>
                  <th style={s.th}>Amount</th>
                  <th style={s.th}>Description</th>
                  <th style={s.th}>Status</th>
                </tr>
              </thead>
              <tbody>
                {pageData.content.length === 0 && (
                  <tr>
                    <td colSpan="5" style={{ textAlign: "center", padding: "2rem", color: "#888" }}>
                      No transactions yet.
                    </td>
                  </tr>
                )}
                {pageData.content.map(tx => (
                  <tr key={tx.id} style={s.row}>
                    <td style={s.td}>{new Date(tx.createdAt).toLocaleString()}</td>
                    <td style={{ ...s.td, ...typeStyle(tx.type) }}>{tx.type}</td>
                    <td style={s.td}>₹ {Number(tx.amount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}</td>
                    <td style={s.td}>{tx.description || "—"}</td>
                    <td style={s.td}>{tx.status}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            <div style={s.pagination}>
              <button
                style={s.pageBtn}
                disabled={pageData.first}
                onClick={() => setPage(p => p - 1)}
              >
                ← Prev
              </button>
              <span style={s.pageInfo}>
                Page {pageData.pageNumber + 1} of {Math.max(pageData.totalPages, 1)}
              </span>
              <button
                style={s.pageBtn}
                disabled={pageData.last}
                onClick={() => setPage(p => p + 1)}
              >
                Next →
              </button>
            </div>
          </>
      }
    </div>
  );
}

const s = {
  page:       { maxWidth: "720px", margin: "2rem auto", padding: "0 1rem" },
  header:     { display: "flex", justifyContent: "space-between", alignItems: "center",
                marginBottom: "1rem" },
  back:       { textDecoration: "none", color: "#555", fontSize: "0.9rem" },
  error:      { color: "#d32f2f", background: "#fdecea", padding: "0.6rem",
                borderRadius: "4px" },
  meta:       { color: "#888", fontSize: "0.85rem", marginBottom: "0.5rem" },
  table:      { width: "100%", borderCollapse: "collapse", fontSize: "0.9rem" },
  theadRow:   { background: "#f5f5f5" },
  th:         { padding: "0.6rem 0.8rem", textAlign: "left", fontWeight: "600",
                borderBottom: "2px solid #ddd" },
  td:         { padding: "0.6rem 0.8rem", borderBottom: "1px solid #eee" },
  row:        {},
  pagination: { display: "flex", justifyContent: "center", alignItems: "center",
                gap: "1rem", marginTop: "1.5rem" },
  pageBtn:    { padding: "0.4rem 0.8rem", cursor: "pointer", borderRadius: "4px",
                border: "1px solid #ccc" },
  pageInfo:   { color: "#555", fontSize: "0.9rem" },
};