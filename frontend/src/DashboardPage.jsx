import { useState, useCallback, useEffect } from "react";
import { usePlaidLink } from "react-plaid-link";
import { useNavigate } from "react-router-dom";
import { plaidApi } from "./api";
import { useAuth } from "./AuthContext";
import api from "./api";

export default function DashboardPage() {
  const [linkToken, setLinkToken] = useState(null);
  const [connected, setConnected] = useState(false);
  const [transactions, setTransactions] = useState([]);
  const [statusMessage, setStatusMessage] = useState("");
  const [loadingToken, setLoadingToken] = useState(true);
  const [loadingTransactions, setLoadingTransactions] = useState(false);

  const { user, logout } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    plaidApi.createLinkToken()
      .then((res) => setLinkToken(res.data.link_token))
      .catch(() => setStatusMessage("Couldn't start bank connection. Try refreshing."))
      .finally(() => setLoadingToken(false));

    fetchTransactions();
  }, []);

  const fetchTransactions = () => {
    setLoadingTransactions(true);
    api.get("/transactions")
      .then((res) => {
        if (res.data.length > 0) {
          setConnected(true);
          setTransactions(res.data);
        }
      })
      .catch(console.error)
      .finally(() => setLoadingTransactions(false));
  };

  const onSuccess = useCallback((publicToken) => {
    setStatusMessage("Connecting your account...");
    plaidApi.exchangeToken(publicToken)
      .then(() => {
        setStatusMessage("Syncing transactions...");
        return api.post("/transactions/sync");
      })
      .then(() => {
        setConnected(true);
        fetchTransactions();
        setStatusMessage("");
      })
      .catch(() => setStatusMessage("Something went wrong connecting your bank."));
  }, []);

  const { open, ready } = usePlaidLink({ token: linkToken, onSuccess });

  const handleLogout = () => { logout(); navigate("/"); };

  // Group transactions by category for the spending summary
  const categoryTotals = transactions.reduce((acc, tx) => {
    if (tx.amount <= 0) return acc;
    const cat = tx.plaidCategory || tx.userCategory || "Uncategorized";
    acc[cat] = (acc[cat] || 0) + tx.amount;
    return acc;
  }, {});

  const totalSpent = Object.values(categoryTotals).reduce((a, b) => a + b, 0);

  return (
    <div style={styles.container}>
      <header style={styles.header}>
        <h1 style={styles.logo}>CashMatrix</h1>
        <div style={styles.headerRight}>
          <span style={styles.userName}>{user?.fullName}</span>
          <button onClick={handleLogout} style={styles.logoutButton}>Log out</button>
        </div>
      </header>

      <main style={styles.main}>
        {!connected ? (
          <div style={styles.card}>
            <h2 style={styles.cardTitle}>Connect your bank account</h2>
            <p style={styles.cardSubtitle}>
              Securely link a bank account to start tracking your expenses automatically.
            </p>
            <button
              onClick={() => open()}
              disabled={!ready || loadingToken}
              style={styles.connectButton}
            >
              {loadingToken ? "Loading..." : "Connect a bank account"}
            </button>
            {statusMessage && <p style={styles.status}>{statusMessage}</p>}
          </div>
        ) : (
          <div style={styles.dashboard}>

            {/* Spending Summary */}
            <div style={styles.card}>
              <h2 style={styles.cardTitle}>Spending by Category</h2>
              <p style={styles.totalSpent}>
                Total spent: <strong>${totalSpent.toFixed(2)}</strong>
              </p>
              {Object.entries(categoryTotals).map(([cat, amount]) => (
                <div key={cat} style={styles.categoryRow}>
                  <div style={styles.categoryInfo}>
                    <span style={styles.categoryName}>{cat}</span>
                    <span style={styles.categoryAmount}>${amount.toFixed(2)}</span>
                  </div>
                  <div style={styles.barTrack}>
                    <div
                      style={{
                        ...styles.barFill,
                        width: `${(amount / totalSpent) * 100}%`,
                      }}
                    />
                  </div>
                </div>
              ))}
              {Object.keys(categoryTotals).length === 0 && (
                <p style={styles.empty}>No spending data yet.</p>
              )}
            </div>

            {/* Transaction List */}
            <div style={styles.card}>
              <div style={styles.cardHeader}>
                <h2 style={styles.cardTitle}>Transactions</h2>
                <button onClick={fetchTransactions} style={styles.refreshButton}>
                  Refresh
                </button>
              </div>
              {loadingTransactions ? (
                <p style={styles.empty}>Loading...</p>
              ) : transactions.length === 0 ? (
                <p style={styles.empty}>No transactions yet.</p>
              ) : (
                transactions.map((tx) => (
                  <div key={tx.id} style={styles.txRow}>
                    <div style={styles.txLeft}>
                      <span style={styles.txName}>{tx.name}</span>
                      <span style={styles.txCategory}>
                        {tx.userCategory || tx.plaidCategory || "Uncategorized"}
                      </span>
                    </div>
                    <div style={styles.txRight}>
                      <span style={{
                        ...styles.txAmount,
                        color: tx.amount < 0 ? "#16a34a" : "#111"
                      }}>
                        {tx.amount < 0 ? "+" : ""}${Math.abs(tx.amount).toFixed(2)}
                      </span>
                      <span style={styles.txDate}>{tx.transactionDate}</span>
                    </div>
                  </div>
                ))
              )}
            </div>

          </div>
        )}
      </main>
    </div>
  );
}

const styles = {
  container: { minHeight: "100vh", backgroundColor: "#f5f5f7", fontFamily: "system-ui, sans-serif" },
  header: { display: "flex", justifyContent: "space-between", alignItems: "center", padding: "1rem 2rem", backgroundColor: "white", borderBottom: "1px solid #eee" },
  logo: { fontSize: "1.25rem", fontWeight: 700, margin: 0 },
  headerRight: { display: "flex", alignItems: "center", gap: "1rem" },
  userName: { color: "#444", fontSize: "0.9rem" },
  logoutButton: { padding: "0.5rem 1rem", borderRadius: "8px", border: "1px solid #ddd", backgroundColor: "white", cursor: "pointer", fontSize: "0.85rem" },
  main: { maxWidth: "800px", margin: "0 auto", padding: "2rem 1rem" },
  dashboard: { display: "flex", flexDirection: "column", gap: "1.5rem" },
  card: { backgroundColor: "white", padding: "1.5rem", borderRadius: "12px", boxShadow: "0 2px 12px rgba(0,0,0,0.06)" },
  cardHeader: { display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" },
  cardTitle: { fontSize: "1.1rem", fontWeight: 700, margin: 0 },
  cardSubtitle: { color: "#666", fontSize: "0.9rem", marginBottom: "1.5rem" },
  totalSpent: { color: "#444", fontSize: "0.95rem", marginBottom: "1rem" },
  categoryRow: { marginBottom: "0.75rem" },
  categoryInfo: { display: "flex", justifyContent: "space-between", marginBottom: "0.25rem" },
  categoryName: { fontSize: "0.9rem", color: "#333" },
  categoryAmount: { fontSize: "0.9rem", fontWeight: 600 },
  barTrack: { height: "6px", backgroundColor: "#f0f0f0", borderRadius: "3px" },
  barFill: { height: "6px", backgroundColor: "#2563eb", borderRadius: "3px", transition: "width 0.3s ease" },
  connectButton: { padding: "0.85rem 1.5rem", borderRadius: "8px", border: "none", backgroundColor: "#2563eb", color: "white", fontWeight: 600, fontSize: "1rem", cursor: "pointer" },
  refreshButton: { padding: "0.4rem 0.85rem", borderRadius: "6px", border: "1px solid #ddd", backgroundColor: "white", cursor: "pointer", fontSize: "0.8rem" },
  txRow: { display: "flex", justifyContent: "space-between", alignItems: "center", padding: "0.75rem 0", borderBottom: "1px solid #f5f5f5" },
  txLeft: { display: "flex", flexDirection: "column", gap: "0.2rem" },
  txName: { fontSize: "0.95rem", fontWeight: 500 },
  txCategory: { fontSize: "0.8rem", color: "#999" },
  txRight: { display: "flex", flexDirection: "column", alignItems: "flex-end", gap: "0.2rem" },
  txAmount: { fontSize: "0.95rem", fontWeight: 600 },
  txDate: { fontSize: "0.8rem", color: "#999" },
  status: { marginTop: "1rem", fontSize: "0.85rem", color: "#444" },
  empty: { color: "#999", fontSize: "0.9rem", textAlign: "center", padding: "1rem 0" },
};