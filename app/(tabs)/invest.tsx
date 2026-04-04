import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, FlatList, ActivityIndicator, RefreshControl, TouchableOpacity, TextInput, Alert, KeyboardAvoidingView, Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Ionicons } from '@expo/vector-icons';

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

  // --- RENDER CARD ---
  const renderInvestment = ({ item }: { item: any }) => {
    const retAmount = parseFloat(item.total_curr || 0) - parseFloat(item.total_inv || 0);
    const isPositive = retAmount >= 0;

    return (
      <View style={styles.card}>
        <View style={styles.cardHeader}>
          <Text style={styles.cardDate}>📅 {formatDate(item.date)}</Text>
          <Text style={{ fontSize: 18 }}>{item.total_status}</Text>
        </View>

        <View style={styles.row}>
          <View>
            <Text style={styles.label}>Invested</Text>
            <Text style={styles.value}>{formatMoney(item.total_inv)}</Text>
          </View>
          <View style={{ alignItems: 'flex-end' }}>
            <Text style={styles.label}>Current Value</Text>
            <Text style={styles.value}>{formatMoney(item.total_curr)}</Text>
          </View>
        </View>

        <View style={[styles.row, { borderTopWidth: 1, borderTopColor: 'rgba(255,255,255,0.05)', paddingTop: 12, marginTop: 12 }]}>
          <Text style={styles.label}>Total Returns</Text>
          <View style={{ alignItems: 'flex-end' }}>
            <Text style={[styles.value, { color: isPositive ? '#34d399' : '#f87171' }]}>
              {isPositive ? '+' : ''}{formatMoney(retAmount)}
            </Text>
            <Text style={[styles.pct, { color: isPositive ? '#34d399' : '#f87171' }]}>
              {isPositive ? '+' : ''}{parseFloat(item.total_ret_pct).toFixed(2)}%
            </Text>
          </View>
        </View>
      </View>
    );
  };

  if (loading) return <View style={styles.centerContainer}><ActivityIndicator size="large" color="#6366f1" /></View>;

  return (
    <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Investments</Text>
        <Text style={styles.recordCount}>{investments.length} Records</Text>
      </View>

      {/* --- SYNC CONTROLS --- */}
      <View style={styles.controlsCard}>
        {!showTokenInput ? (
          <View style={styles.btnRow}>
            <TouchableOpacity style={[styles.syncBtn, { backgroundColor: '#0d9488' }]} onPress={handleSyncToSheets} disabled={syncingSheets}>
              {syncingSheets ? <ActivityIndicator color="#fff" size="small" /> : <Text style={styles.btnText}>📥 Sync Sheets</Text>}
            </TouchableOpacity>
            
            <TouchableOpacity style={[styles.syncBtn, { backgroundColor: '#dc2626' }]} onPress={() => setShowTokenInput(true)}>
              <Text style={styles.btnText}>⚡ Kite Sync</Text>
            </TouchableOpacity>
          </View>
        ) : (
          <View style={styles.tokenInputRow}>
            <TextInput
              style={styles.input}
              placeholder="Paste 127.0.0.1 URL here..."
              placeholderTextColor="#64748b"
              value={tokenStr}
              onChangeText={setTokenStr}
            />
            <View style={styles.btnRow}>
              <TouchableOpacity style={[styles.syncBtn, { flex: 1 }]} onPress={handleSubmitToken} disabled={syncing}>
                {syncing ? <ActivityIndicator color="#fff" size="small" /> : <Text style={styles.btnText}>Submit</Text>}
              </TouchableOpacity>
              <TouchableOpacity style={[styles.syncBtn, { flex: 1, backgroundColor: '#1e293b' }]} onPress={() => { setShowTokenInput(false); setTokenStr(""); }}>
                <Text style={styles.btnText}>Cancel</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}
      </View>

      {/* --- LIST --- */}
      <FlatList
        data={investments}
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
  recordCount: { color: '#64748b', fontSize: 12, fontWeight: 'bold' },

  controlsCard: { marginHorizontal: 20, marginBottom: 15, padding: 15, backgroundColor: '#111827', borderRadius: 16, borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)' },
  btnRow: { flexDirection: 'row', gap: 10 },
  syncBtn: { flex: 1, paddingVertical: 12, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  btnText: { color: '#fff', fontWeight: 'bold', fontSize: 14 },
  
  tokenInputRow: { gap: 10 },
  input: { backgroundColor: '#1e293b', color: '#fff', padding: 12, borderRadius: 10, borderWidth: 1, borderColor: 'rgba(255,255,255,0.1)' },

  listContent: { paddingHorizontal: 20, paddingBottom: 40 },
  card: { backgroundColor: '#111827', borderRadius: 16, padding: 16, marginBottom: 15, borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)' },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 15 },
  cardDate: { color: '#e2e8f0', fontWeight: 'bold', fontSize: 14 },
  row: { flexDirection: 'row', justifyContent: 'space-between' },
  label: { color: '#64748b', fontSize: 12, fontWeight: '600', marginBottom: 4 },
  value: { color: '#fff', fontSize: 16, fontWeight: 'bold' },
  pct: { fontSize: 12, fontWeight: 'bold', marginTop: 2 }
});