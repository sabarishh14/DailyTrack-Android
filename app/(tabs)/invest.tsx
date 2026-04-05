import { Ionicons } from '@expo/vector-icons';
import AsyncStorage from '@react-native-async-storage/async-storage';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, KeyboardAvoidingView, Platform, RefreshControl, ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';

// 🛑 REMEMBER TO UPDATE THIS IP ADDRESS!
const API_URL ="https://sabarishhh14-dailytrack-v2.hf.space/api";

export default function InvestScreen() {
  const [investments, setInvestments] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  // Kite Sync State
  const [showTokenInput, setShowTokenInput] = useState(false);
  const [tokenStr, setTokenStr] = useState("");
  const [syncing, setSyncing] = useState(false);
  const [syncingSheets, setSyncingSheets] = useState(false);

  // --- Filter & Sorting Engine ---
  const MONTH_NAMES = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  const [showFilters, setShowFilters] = useState(false);
  const [filterMonth, setFilterMonth] = useState("All");
  const [sortBy, setSortBy] = useState<'Date' | 'Returns' | 'Invested'>('Date');

  // Generate available months for the chips
  const availableMonths = useMemo(() => {
    const m = new Set(investments.map(inv => {
      if (!inv.date) return null;
      const d = new Date(inv.date);
      return `${MONTH_NAMES[d.getMonth()]} ${d.getFullYear()}`;
    }).filter((month): month is string => month !== null));
    return ["All", ...Array.from(m)];
  }, [investments]);

  // Apply filters and sorting
  const processedInvestments = useMemo(() => {
    let data = [...investments];

    // Filter
    if (filterMonth !== "All") {
      data = data.filter(inv => {
        if (!inv.date) return false;
        const d = new Date(inv.date);
        return `${MONTH_NAMES[d.getMonth()]} ${d.getFullYear()}` === filterMonth;
      });
    }

    // Sort
    data.sort((a, b) => {
      if (sortBy === 'Date') {
        return new Date(b.date).getTime() - new Date(a.date).getTime();
      } else if (sortBy === 'Returns') {
        const retA = parseFloat(a.total_curr || 0) - parseFloat(a.total_inv || 0);
        const retB = parseFloat(b.total_curr || 0) - parseFloat(b.total_inv || 0);
        return retB - retA; // Highest returns first
      } else if (sortBy === 'Invested') {
        return parseFloat(b.total_inv || 0) - parseFloat(a.total_inv || 0);
      }
      return 0;
    });

    return data;
  }, [investments, filterMonth, sortBy]);

  const fetchInvestments = async () => {
    try {
      const token = await AsyncStorage.getItem('dt_token');
      const res = await fetch(`${API_URL}/investments`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      
      if (res.ok) setInvestments(await res.json());
    } catch (error) {
      console.error("Failed to fetch investments:", error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => { fetchInvestments(); }, []);
  const onRefresh = useCallback(() => { setRefreshing(true); fetchInvestments(); }, []);

  const formatMoney = (amount: number) => "₹" + Number(amount).toLocaleString("en-IN", { maximumFractionDigits: 2 });
  const formatDate = (dateStr: string) => {
    if (!dateStr) return "";
    const d = new Date(dateStr);
    return `${d.getDate()}/${d.getMonth() + 1}/${String(d.getFullYear()).slice(2)}`;
  };

  // --- ACTIONS ---
  const handleSubmitToken = async () => {
    let token = tokenStr.trim();
    if (token.includes("request_token=")) {
      const match = token.match(/request_token=([^&]+)/);
      if (match) token = match[1];
    }

    if (!token) return Alert.alert("Hold up", "Please paste the full URL containing the request_token.");

    setSyncing(true);
    try {
      const authToken = await AsyncStorage.getItem('dt_token');
      const res = await fetch(`${API_URL}/sync/kite`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${authToken}` },
        body: JSON.stringify({ request_token: token })
      });
      const data = await res.json();
      
      if (data.success) {
        Alert.alert("Success", data.message);
        fetchInvestments();
        setShowTokenInput(false);
        setTokenStr("");
      } else {
        Alert.alert("Sync Failed", data.message);
      }
    } catch (e: any) {
      Alert.alert("Error", e.message);
    } finally {
      setSyncing(false);
    }
  };

  const handleSyncToSheets = async () => {
    setSyncingSheets(true);
    try {
      const authToken = await AsyncStorage.getItem('dt_token');
      const res = await fetch(`${API_URL}/sync/investments-to-sheets`, { 
        method: 'POST', 
        headers: { 'Authorization': `Bearer ${authToken}` } 
      });
      const data = await res.json();
      Alert.alert("Sheets Sync", data.message);
    } catch (e: any) {
      Alert.alert("Error", e.message);
    } finally {
      setSyncingSheets(false);
    }
  };

  // --- RENDER PREMIUM CARD ---
  const renderInvestment = ({ item }: { item: any }) => {
    const retAmount = parseFloat(item.total_curr || 0) - parseFloat(item.total_inv || 0);
    const retPct = parseFloat(item.total_ret_pct || 0);
    const isPositive = retAmount >= 0;
    const trendColor = isPositive ? '#34d399' : '#f87171';
    const trendBg = isPositive ? 'rgba(52,211,153,0.1)' : 'rgba(248,113,113,0.1)';

    // --- NEW: Intercept and clean the backend status ---
    let statusIcon = "radio-button-on";
    let statusLabel = "Active";
    let statusColor = "#94a3b8";
    let statusBg = "#1e293b";

    const rawStatus = item.total_status || "";
    if (rawStatus.includes('⬆️') || rawStatus.includes('💹')) {
      statusIcon = "trending-up";
      statusLabel = "Market Up";
      statusColor = "#34d399";
      statusBg = "rgba(52,211,153,0.15)";
    } else if (rawStatus.includes('⬇️') || rawStatus.includes('📉')) {
      statusIcon = "trending-down";
      statusLabel = "Market Down";
      statusColor = "#f87171";
      statusBg = "rgba(248,113,113,0.15)";
    } else if (rawStatus) {
      // Fallback: Strip out emojis if it's some other random status
      statusLabel = rawStatus.replace(/[\u{1F600}-\u{1F64F}\u{1F300}-\u{1F5FF}\u{1F680}-\u{1F6FF}\u{1F700}-\u{1F77F}\u{1F780}-\u{1F7FF}\u{1F800}-\u{1F8FF}\u{1F900}-\u{1F9FF}\u{1FA00}-\u{1FA6F}\u{1FA70}-\u{1FAFF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/gu, '').trim() || "Active";
    }

    return (
      <View style={[styles.card, { borderLeftColor: trendColor, borderLeftWidth: 3 }]}>
        {/* Top Header */}
        <View style={styles.cardHeader}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
            <Ionicons name="calendar-outline" size={16} color="#94a3b8" />
            <Text style={styles.cardDate}>{formatDate(item.date)}</Text>
          </View>
          <View style={[styles.statusBadge, { backgroundColor: statusBg, flexDirection: 'row', alignItems: 'center', gap: 4 }]}>
            <Ionicons name={statusIcon as any} size={12} color={statusColor} />
            <Text style={[styles.statusText, { color: statusColor }]}>{statusLabel}</Text>
          </View>
        </View>

        {/* Middle: Invested vs Current */}
        <View style={styles.comparisonRow}>
          <View style={styles.metricBlock}>
            <Text style={styles.label}>INVESTED</Text>
            <Text style={styles.value}>{formatMoney(item.total_inv)}</Text>
          </View>
          
          <Ionicons name="arrow-forward-outline" size={20} color="#475569" style={{ marginHorizontal: 10 }} />
          
          <View style={[styles.metricBlock, { alignItems: 'flex-end' }]}>
            <Text style={styles.label}>CURRENT VALUE</Text>
            <Text style={styles.value}>{formatMoney(item.total_curr)}</Text>
          </View>
        </View>

        {/* Bottom: Returns */}
        <View style={styles.returnsRow}>
          <Text style={styles.label}>TOTAL RETURNS</Text>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <Text style={[styles.retAmount, { color: trendColor }]}>
              {isPositive ? '+' : ''}{formatMoney(retAmount)}
            </Text>
            <View style={[styles.pctBadge, { backgroundColor: trendBg }]}>
              <Ionicons name={isPositive ? "trending-up" : "trending-down"} size={12} color={trendColor} />
              <Text style={[styles.pctText, { color: trendColor }]}>
                {isPositive ? '+' : ''}{retPct.toFixed(2)}%
              </Text>
            </View>
          </View>
        </View>
      </View>
    );
  };

  if (loading) return <View style={styles.centerContainer}><ActivityIndicator size="large" color="#6366f1" /></View>;

  return (
    <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      {/* Dynamic Header */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Investments</Text>
        <TouchableOpacity 
          onPress={() => setShowFilters(!showFilters)} 
          style={[styles.filterToggleBadge, showFilters && {backgroundColor: '#6366f1'}]}
        >
          <Ionicons name="filter" size={14} color={showFilters ? "#fff" : "#94a3b8"} />
          <Text style={[styles.filterToggleText, showFilters && {color: '#fff'}]}>
            {showFilters ? "Close Filters" : "Filters"}
          </Text>
        </TouchableOpacity>
      </View>

      {/* Elegant Filter Card */}
      {showFilters && (
        <View style={styles.filtersCard}>
          <Text style={styles.filterLabel}>SORT BY</Text>
          <View style={styles.filterRow}>
            {['Date', 'Returns', 'Invested'].map(s => (
              <TouchableOpacity key={s} style={[styles.filterChip, sortBy === s && styles.filterChipActive]} onPress={() => setSortBy(s as any)}>
                <Text style={[styles.filterChipText, sortBy === s && styles.filterChipTextActive]}>{s}</Text>
              </TouchableOpacity>
            ))}
          </View>

          <Text style={[styles.filterLabel, { marginTop: 10 }]}>MONTH</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ flexDirection: 'row' }}>
            {availableMonths.map(m => (
              <TouchableOpacity key={m} style={[styles.filterChip, filterMonth === m && styles.filterChipActive]} onPress={() => setFilterMonth(m)}>
                <Text style={[styles.filterChipText, filterMonth === m && styles.filterChipTextActive]}>{m}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
        </View>
      )}

      {/* --- SYNC CONTROLS (Refined) --- */}
      <View style={styles.controlsWrapper}>
        {!showTokenInput ? (
          <View style={styles.btnRow}>
            <TouchableOpacity style={styles.syncBtnGhost} onPress={handleSyncToSheets} disabled={syncingSheets}>
              {syncingSheets ? <ActivityIndicator color="#0d9488" size="small" /> : <Text style={styles.btnTextGhost}>📥 Sync Sheets</Text>}
            </TouchableOpacity>
            
            <TouchableOpacity style={styles.syncBtnPrimary} onPress={() => setShowTokenInput(true)}>
              <Text style={styles.btnTextPrimary}>⚡ Kite Sync</Text>
            </TouchableOpacity>
          </View>
        ) : (
          <View style={styles.tokenInputRow}>
            <TextInput style={styles.input} placeholder="Paste 127.0.0.1 URL here..." placeholderTextColor="#64748b" value={tokenStr} onChangeText={setTokenStr} />
            <View style={styles.btnRow}>
              <TouchableOpacity style={[styles.syncBtnPrimary, { flex: 1 }]} onPress={handleSubmitToken} disabled={syncing}>
                {syncing ? <ActivityIndicator color="#fff" size="small" /> : <Text style={styles.btnTextPrimary}>Submit</Text>}
              </TouchableOpacity>
              <TouchableOpacity style={[styles.syncBtnGhost, { flex: 1 }]} onPress={() => { setShowTokenInput(false); setTokenStr(""); }}>
                <Text style={styles.btnTextGhost}>Cancel</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}
      </View>

      {/* --- LIST HEADER (Replaces the ugly line) --- */}
      <View style={styles.listHeaderRow}>
        <View style={styles.countBadge}>
          <Text style={styles.countBadgeText}>{processedInvestments.length} Snapshots</Text>
        </View>
      </View>

      {/* --- LIST --- */}
      <FlatList
        data={processedInvestments}
        keyExtractor={(item) => item.id.toString()}
        renderItem={renderInvestment}
        contentContainerStyle={styles.listContent}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#6366f1" />}
        ListEmptyComponent={<Text style={{ color: '#64748b', textAlign: 'center', marginTop: 40 }}>📭 No investment data.</Text>}
      />
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080b12' },
  centerContainer: { flex: 1, backgroundColor: '#080b12', justifyContent: 'center', alignItems: 'center' },
  header: { marginTop: 60, marginBottom: 15, paddingHorizontal: 20, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  headerTitle: { fontSize: 24, fontWeight: 'bold', color: '#fff' },
  // --- LIST HEADER STYLES ---
  listHeaderRow: { marginHorizontal: 20, marginBottom: 15, flexDirection: 'row', justifyContent: 'center' },
  countBadge: { backgroundColor: 'rgba(255,255,255,0.03)', paddingHorizontal: 14, paddingVertical: 6, borderRadius: 20, borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)' },
  countBadgeText: { color: '#64748b', fontSize: 12, fontWeight: 'bold', letterSpacing: 0.5 },
  controlsCard: { marginHorizontal: 20, marginBottom: 15, padding: 15, backgroundColor: '#111827', borderRadius: 16, borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)' },
  syncBtn: { flex: 1, paddingVertical: 12, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  btnText: { color: '#fff', fontWeight: 'bold', fontSize: 14 },

  row: { flexDirection: 'row', justifyContent: 'space-between' },
  pct: { fontSize: 12, fontWeight: 'bold', marginTop: 2 },
  // --- HEADER & FILTER STYLES ---
  filterToggleBadge: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#1e293b', paddingHorizontal: 12, paddingVertical: 6, borderRadius: 12, borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)' },
  filterToggleText: { color: '#94a3b8', fontSize: 12, fontWeight: '600', marginLeft: 6 },
  
  filtersCard: { backgroundColor: '#0f172a', marginHorizontal: 20, borderRadius: 16, padding: 16, marginBottom: 15, borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)' },
  filterLabel: { fontSize: 10, color: '#64748b', fontWeight: 'bold', marginBottom: 8, letterSpacing: 0.5 },
  filterRow: { flexDirection: 'row', gap: 8 },
  filterChip: { paddingHorizontal: 14, paddingVertical: 8, borderRadius: 16, backgroundColor: '#1e293b', borderWidth: 1, borderColor: 'rgba(255,255,255,0.03)', marginRight: 8 },
  filterChipActive: { backgroundColor: 'rgba(99,102,241,0.15)', borderColor: '#6366f1' },
  filterChipText: { color: '#94a3b8', fontSize: 13, fontWeight: '500' },
  filterChipTextActive: { color: '#a5b4fc', fontWeight: 'bold' },

  // --- SYNC CONTROLS ---
  controlsWrapper: { marginHorizontal: 20, marginBottom: 10 },
  btnRow: { flexDirection: 'row', gap: 10 },
  syncBtnPrimary: { flex: 1, backgroundColor: 'rgba(220,38,38,0.1)', borderColor: '#dc2626', borderWidth: 1, paddingVertical: 12, borderRadius: 10, alignItems: 'center' },
  btnTextPrimary: { color: '#ef4444', fontWeight: 'bold', fontSize: 14 },
  syncBtnGhost: { flex: 1, backgroundColor: 'rgba(13,148,136,0.05)', borderColor: 'rgba(13,148,136,0.3)', borderWidth: 1, paddingVertical: 12, borderRadius: 10, alignItems: 'center' },
  btnTextGhost: { color: '#14b8a6', fontWeight: 'bold', fontSize: 14 },
  tokenInputRow: { gap: 10 },
  input: { backgroundColor: '#111827', color: '#fff', padding: 14, borderRadius: 10, borderWidth: 1, borderColor: 'rgba(255,255,255,0.1)' },

  // --- PREMIUM CARD STYLES ---
  listContent: { paddingHorizontal: 20, paddingBottom: 40 },
  card: { backgroundColor: '#111827', borderRadius: 14, padding: 18, marginBottom: 15, borderWidth: 1, borderColor: 'rgba(255,255,255,0.03)' },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 },
  cardDate: { color: '#cbd5e1', fontWeight: '600', fontSize: 14 },
  statusBadge: { backgroundColor: '#1e293b', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 8 },
  statusText: { color: '#94a3b8', fontSize: 11, fontWeight: 'bold' },
  
  comparisonRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, backgroundColor: 'rgba(255,255,255,0.02)', padding: 12, borderRadius: 10 },
  metricBlock: { flex: 1 },
  label: { color: '#64748b', fontSize: 11, fontWeight: 'bold', marginBottom: 4, letterSpacing: 0.5 },
  value: { color: '#f8fafc', fontSize: 17, fontWeight: 'bold', fontFamily: 'System' },
  
  returnsRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingTop: 15, borderTopWidth: 1, borderTopColor: 'rgba(255,255,255,0.05)' },
  retAmount: { fontSize: 17, fontWeight: 'bold', fontFamily: 'System' },
  pctBadge: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 8, paddingVertical: 4, borderRadius: 6 },
  pctText: { fontSize: 12, fontWeight: 'bold' },
});