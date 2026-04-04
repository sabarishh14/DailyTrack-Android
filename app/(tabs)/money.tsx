import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, FlatList, ActivityIndicator, RefreshControl, TouchableOpacity } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Ionicons } from '@expo/vector-icons';

// 🛑 REMEMBER TO UPDATE THIS IP ADDRESS!
const API_URL ="https://sabarishhh14-dailytrack-v2.hf.space/api";

const BANKS: Record<string, string> = {
  KOTAK: "🔴", IDBI: "🟢", FEDERAL: "🟠", CUB: "🟣", INDIAN: "🔵", ICICI: "🟡", "CC-PINNACLE 6360": "💳", "Cash": "💵"
};

export default function MoneyScreen() {
  const [transactions, setTransactions] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const fetchTransactions = async () => {
    try {
      const token = await AsyncStorage.getItem('dt_token');
      // Fetching the 100 most recent transactions
      const res = await fetch(`${API_URL}/transactions?limit=100&offset=0`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      
      if (res.ok) {
        const data = await res.json();
        setTransactions(data.transactions || []);
      }
    } catch (error) {
      console.error("Failed to fetch transactions:", error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchTransactions();
  }, []);

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    fetchTransactions();
  }, []);

  const formatMoney = (amount: number) => 
    "₹" + Number(Math.abs(amount)).toLocaleString("en-IN", { maximumFractionDigits: 2 });

  const formatDate = (dateStr: string) => {
    if (!dateStr) return "";
    const d = new Date(dateStr);
    return `${d.getDate()}/${d.getMonth() + 1}/${String(d.getFullYear()).slice(2)}`;
  };

  const renderTransaction = ({ item }: { item: any }) => {
    const isCredit = item.type === 'Credit';
    const isInvestment = item.type === 'investment';
    
    let amountColor = '#6366f1'; // default accent
    if (isCredit) amountColor = '#34d399'; // green
    if (item.type === 'Debit') amountColor = '#f87171'; // red
    if (isInvestment) amountColor = '#3b82f6'; // blue

    return (
      <TouchableOpacity style={styles.txCard} activeOpacity={0.7}>
        <View style={styles.txLeft}>
          <View style={styles.iconCircle}>
            <Text style={{ fontSize: 18 }}>{BANKS[item.account] || "🏦"}</Text>
          </View>
          <View>
            <Text style={styles.txHeading} numberOfLines={1}>{item.heading}</Text>
            <Text style={styles.txSub}>
              {formatDate(item.date)} • {item.account}
            </Text>
          </View>
        </View>

        <View style={styles.txRight}>
          <Text style={[styles.txAmount, { color: amountColor }]}>
            {item.type === 'Debit' ? '-' : '+'}{formatMoney(item.amount)}
          </Text>
          {item.description ? (
            <Text style={styles.txDesc} numberOfLines={1}>{item.description}</Text>
          ) : null}
        </View>
      </TouchableOpacity>
    );
  };

  if (loading) {
    return (
      <View style={styles.centerContainer}>
        <ActivityIndicator size="large" color="#6366f1" />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Transactions</Text>
        <View style={styles.badge}>
          <Text style={styles.badgeText}>{transactions.length} Recent</Text>
        </View>
      </View>

      <FlatList
        data={transactions}
        keyExtractor={(item) => item.id.toString()}
        renderItem={renderTransaction}
        contentContainerStyle={styles.listContent}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#6366f1" />}
        ListEmptyComponent={
          <Text style={styles.emptyText}>📭 No transactions found.</Text>
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080b12' },
  centerContainer: { flex: 1, backgroundColor: '#080b12', justifyContent: 'center', alignItems: 'center' },
  
  header: { 
    marginTop: 60, 
    marginBottom: 15, 
    paddingHorizontal: 20,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center'
  },
  headerTitle: { fontSize: 24, fontWeight: 'bold', color: '#fff' },
  badge: {
    backgroundColor: '#1e293b',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.1)'
  },
  badgeText: { color: '#94a3b8', fontSize: 12, fontWeight: '600' },

  listContent: { paddingHorizontal: 20, paddingBottom: 40 },
  
  txCard: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#111827',
    padding: 16,
    borderRadius: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.05)'
  },
  txLeft: { flexDirection: 'row', alignItems: 'center', flex: 1 },
  iconCircle: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(255,255,255,0.03)',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.05)'
  },
  txHeading: { color: '#e2e8f0', fontSize: 15, fontWeight: 'bold', marginBottom: 4 },
  txSub: { color: '#64748b', fontSize: 12 },
  
  txRight: { alignItems: 'flex-end', maxWidth: '40%' },
  txAmount: { fontSize: 16, fontWeight: 'bold', fontFamily: 'System' },
  txDesc: { color: '#64748b', fontSize: 11, marginTop: 4 },

  emptyText: { color: '#64748b', textAlign: 'center', marginTop: 40, fontSize: 14 }
});