import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, ScrollView, StyleSheet, RefreshControl, ActivityIndicator, TouchableOpacity } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useRouter } from 'expo-router';

// 🛑 REPLACE THIS WITH YOUR COMPUTER'S ACTUAL WI-FI IP ADDRESS!
const API_URL ="https://sabarishhh14-dailytrack-v2.hf.space/api";

const BANKS: Record<string, { emoji: string; color: string }> = {
  KOTAK: { emoji: "🔴", color: "#ef4444" },
  IDBI: { emoji: "🟢", color: "#22c55e" },
  FEDERAL: { emoji: "🟠", color: "#f97316" },
  CUB: { emoji: "🟣", color: "#a855f7" },
  INDIAN: { emoji: "🔵", color: "#3b82f6" },
  ICICI: { emoji: "🟡", color: "#eab308" },
  "CC-PINNACLE 6360": { emoji: "💳", color: "#ec4899" },
  "Cash": { emoji: "💵", color: "#10b981" },
};

const getBankEmoji = (accountName: string) => BANKS[accountName]?.emoji || "🏦";
const getBankColor = (accountName: string) => BANKS[accountName]?.color || "#6366f1";

export default function HomeScreen() {
  const [accounts, setAccounts] = useState<any[]>([]);
  const [physical, setPhysical] = useState<any[]>([]);
  const [investments, setInvestments] = useState<any[]>([]);
  
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [showBalances, setShowBalances] = useState(false);
  const router = useRouter();

  const handleLogout = async () => {
    await AsyncStorage.removeItem('dt_token');
    router.replace('/login' as any);
  };
  const fetchAllData = async () => {
    try {
      const token = await AsyncStorage.getItem('dt_token');
      const headers = { 'Authorization': `Bearer ${token}` };

      // Fetch all data in parallel!
      const [accRes, phyRes, invRes] = await Promise.all([
        fetch(`${API_URL}/accounts`, { headers }),
        fetch(`${API_URL}/physical`, { headers }),
        fetch(`${API_URL}/investments`, { headers })
      ]);
      
      if (accRes.ok) setAccounts(await accRes.json());
      if (phyRes.ok) setPhysical(await phyRes.json());
      if (invRes.ok) setInvestments(await invRes.json());

    } catch (error) {
      console.error("Failed to fetch data:", error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchAllData();
  }, []);

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    fetchAllData();
  }, []);

  // --- CALCULATIONS (Mirroring your App.jsx) ---
  const formatMoney = (amount: number) => 
    "₹" + Number(amount).toLocaleString("en-IN", { maximumFractionDigits: 2 });

  const netWorth = accounts
    .filter(a => a.balance_tracked)
    .reduce((sum, a) => sum + parseFloat(a.balance || 0), 0);

  // Physical Activity calculation (Current Month)
  const currentMonth = new Date().getMonth();
  const currentYear = new Date().getFullYear();
  const physActive = physical.filter(p => {
    if (!p.date) return false;
    const d = new Date(p.date);
    return d.getMonth() === currentMonth && d.getFullYear() === currentYear &&
      (p.gym || p.badminton || p.table_tennis || p.cricket || p.others);
  }).length;

  // Investment calculations
  const latestInv = investments.length > 0 ? investments[0] : null;
  const totalInvested = latestInv ? parseFloat(latestInv.total_inv || 0) : 0;
  const totalCurrent = latestInv ? parseFloat(latestInv.total_curr || 0) : 0;
  const totalReturn = totalCurrent - totalInvested;
  const totalRetPct = latestInv ? parseFloat(latestInv.total_ret_pct || 0) : 0;

  if (loading) {
    return (
      <View style={styles.centerContainer}>
        <ActivityIndicator size="large" color="#6366f1" />
      </View>
    );
  }

  return (
    <ScrollView 
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#6366f1" />}
    >
      <View style={[styles.header, { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }]}>
        <Text style={styles.headerTitle}>Dashboard</Text>
        <TouchableOpacity 
          onPress={handleLogout} 
          style={{ backgroundColor: 'rgba(248,113,113,0.1)', paddingVertical: 6, paddingHorizontal: 12, borderRadius: 8, borderWidth: 1, borderColor: 'rgba(248,113,113,0.3)' }}
        >
          <Text style={{ color: '#f87171', fontWeight: '600', fontSize: 12 }}>Logout</Text>
        </TouchableOpacity>
      </View>

      {/* --- NET WORTH & PHYSICAL COMBINED ROW --- */}
      <View style={styles.heroRow}>
        <View style={[styles.netWorthCard, { flex: 1.5, marginRight: 10 }]}>
          <View style={styles.nwTopRow}>
            <Text style={styles.nwLabel}>NET WORTH</Text>
            <TouchableOpacity onPress={() => setShowBalances(!showBalances)}>
              <Text style={{ fontSize: 16 }}>{showBalances ? '🙈' : '👁️'}</Text>
            </TouchableOpacity>
          </View>
          <Text style={styles.nwValue}>
            {showBalances ? formatMoney(netWorth) : '₹ ••••••'}
          </Text>
          <Text style={styles.nwSub}>Across {accounts.length} accounts</Text>
        </View>

        <View style={[styles.physCard, { flex: 1 }]}>
          <Text style={styles.physBigNum}>{physActive}</Text>
          <Text style={styles.physLabel}>Days Active</Text>
          <Text style={styles.physSub}>This Month</Text>
        </View>
      </View>

      {/* --- ACCOUNTS GRID --- */}
      <Text style={styles.sectionTitle}>🏦 Account Balances</Text>
      <View style={styles.grid}>
        {accounts
          .filter(a => a.balance_tracked && a.account !== 'CC-PINNACLE 6360' && a.account !== 'Cash')
          .map(a => (
            <View key={a.account} style={[styles.accountCard, { borderLeftColor: getBankColor(a.account) }]}>
              <View style={styles.accTop}>
                <Text style={styles.accEmoji}>{getBankEmoji(a.account)}</Text>
                <Text style={styles.accName}>{a.account}</Text>
              </View>
              <Text style={styles.accBalance}>
                {showBalances ? formatMoney(a.balance) : '₹ ••••••'}
              </Text>
            </View>
          ))}
      </View>

      {/* --- INVESTMENTS --- */}
      <Text style={styles.sectionTitle}>📊 Investments</Text>
      <View style={styles.investCard}>
        <View style={styles.invRow}>
          <Text style={styles.invLabel}>Invested</Text>
          <Text style={styles.invVal}>{showBalances ? formatMoney(totalInvested) : '₹ ••••••'}</Text>
        </View>
        <View style={styles.invRow}>
          <Text style={styles.invLabel}>Current Value</Text>
          <Text style={styles.invVal}>{showBalances ? formatMoney(totalCurrent) : '₹ ••••••'}</Text>
        </View>
        <View style={[styles.invRow, { borderBottomWidth: 0, marginTop: 5 }]}>
          <Text style={styles.invLabel}>Total Returns</Text>
          <View style={{ alignItems: 'flex-end' }}>
            <Text style={[styles.invVal, { color: totalReturn >= 0 ? '#34d399' : '#f87171' }]}>
              {showBalances ? formatMoney(totalReturn) : '₹ ••••••'}
            </Text>
            <Text style={{ color: totalRetPct >= 0 ? '#34d399' : '#f87171', fontSize: 12, fontWeight: 'bold' }}>
              {showBalances ? `${totalRetPct >= 0 ? '+' : ''}${totalRetPct.toFixed(2)}%` : '••••'}
            </Text>
          </View>
        </View>
      </View>
      
      <View style={{ height: 40 }} /> 
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080b12', paddingHorizontal: 20 },
  centerContainer: { flex: 1, backgroundColor: '#080b12', justifyContent: 'center', alignItems: 'center' },
  header: { marginTop: 60, marginBottom: 20 },
  headerTitle: { fontSize: 24, fontWeight: 'bold', color: '#fff' },
  
  heroRow: { flexDirection: 'row', marginBottom: 24 },
  netWorthCard: {
    backgroundColor: '#6366f1',
    borderRadius: 18,
    padding: 20,
    justifyContent: 'center',
  },
  nwTopRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  nwLabel: { fontSize: 11, color: 'rgba(255,255,255,0.7)', fontWeight: 'bold', letterSpacing: 1 },
  nwValue: { fontSize: 26, fontWeight: '800', color: '#fff', marginTop: 8 },
  nwSub: { fontSize: 11, color: 'rgba(255,255,255,0.6)', marginTop: 8 },

  physCard: {
    backgroundColor: '#111827',
    borderColor: 'rgba(255,255,255,0.07)',
    borderWidth: 1,
    borderRadius: 18,
    padding: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  physBigNum: { fontSize: 36, fontWeight: '900', color: '#06b6d4' },
  physLabel: { fontSize: 12, fontWeight: 'bold', color: '#fff', marginTop: 4 },
  physSub: { fontSize: 10, color: '#64748b' },

  sectionTitle: { fontSize: 16, fontWeight: 'bold', color: '#e2e8f0', marginBottom: 15, marginTop: 10 },
  
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between' },
  accountCard: {
    backgroundColor: '#111827',
    borderColor: 'rgba(255,255,255,0.07)',
    borderWidth: 1,
    borderLeftWidth: 4,
    borderRadius: 12,
    padding: 16,
    width: '48%',
    marginBottom: 15,
  },
  accTop: { flexDirection: 'row', alignItems: 'center', marginBottom: 10 },
  accEmoji: { fontSize: 16, marginRight: 6 },
  accName: { fontSize: 11, fontWeight: 'bold', color: '#64748b', letterSpacing: 0.5 },
  accBalance: { fontSize: 16, fontWeight: 'bold', color: '#fff' },

  investCard: {
    backgroundColor: '#111827',
    borderColor: 'rgba(255,255,255,0.07)',
    borderWidth: 1,
    borderRadius: 16,
    padding: 20,
    marginBottom: 20,
  },
  invRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255,255,255,0.05)',
  },
  invLabel: { color: '#94a3b8', fontSize: 14, fontWeight: '500' },
  invVal: { color: '#fff', fontSize: 16, fontWeight: 'bold' }
});