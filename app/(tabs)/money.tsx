import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { View, Text, StyleSheet, FlatList, ActivityIndicator, RefreshControl, TouchableOpacity, Modal, TextInput, Alert, ScrollView, Platform, KeyboardAvoidingView } from 'react-native';
import DateTimePicker from '@react-native-community/datetimepicker';
import { Ionicons } from '@expo/vector-icons';
import AsyncStorage from '@react-native-async-storage/async-storage';

// 🛑 REMEMBER TO UPDATE THIS IP ADDRESS!
const API_URL ="https://sabarishhh14-dailytrack-v2.hf.space/api";

const BANKS: Record<string, string> = {
  KOTAK: "🔴", IDBI: "🟢", FEDERAL: "🟠", CUB: "🟣", INDIAN: "🔵", ICICI: "🟡", "CC-PINNACLE 6360": "💳", "Cash": "💵"
};

export default function MoneyScreen() {
  const [transactions, setTransactions] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  // --- Edit/Delete State ---
  const [editingTx, setEditingTx] = useState<any>(null);
  const [editForm, setEditForm] = useState<any>({});
  const [saving, setSaving] = useState(false);
  const [actionMenuTx, setActionMenuTx] = useState<any>(null); // <-- Add this new state!

// --- Filter & Pagination State ---
  const MONTH_NAMES = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  const [searchQuery, setSearchQuery] = useState("");
  const [filterAccount, setFilterAccount] = useState("All");
  const [filterType, setFilterType] = useState("All");
  const [filterMonth, setFilterMonth] = useState("All");
  
  // New Date States
  const [filterDateFrom, setFilterDateFrom] = useState("");
  const [filterDateTo, setFilterDateTo] = useState("");
  const [showDatePicker, setShowDatePicker] = useState<'from' | 'to' | null>(null);
  
  const [showFilters, setShowFilters] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const rowsPerPage = 15;

  // Reset to page 1 whenever ANY filter changes
  useEffect(() => {
    setCurrentPage(1);
  }, [searchQuery, filterAccount, filterType, filterMonth, filterDateFrom, filterDateTo]);

  const onDateChange = (event: any, selectedDate?: Date) => {
    const currentMode = showDatePicker;
    setShowDatePicker(Platform.OS === 'ios' ? currentMode : null);
    if (selectedDate) {
      const dateStr = selectedDate.toISOString().split('T')[0];
      if (currentMode === 'from') setFilterDateFrom(dateStr);
      if (currentMode === 'to') setFilterDateTo(dateStr);
    }
  };

  // --- Derived Data for Chips ---
  const availableAccounts = ["All", ...Array.from(new Set(transactions.map(t => t.account)))];
  const availableTypes = ["All", ...Array.from(new Set(transactions.map(t => t.type)))];
  const availableMonths = useMemo(() => {
    const m = new Set(transactions.map(t => {
      if (!t.date) return null;
      const d = new Date(t.date);
      return `${MONTH_NAMES[d.getMonth()]} ${d.getFullYear()}`;
    }).filter((month): month is string => month !== null));
    return ["All", ...Array.from(m)];
  }, [transactions]);

  // --- Filtering Engine ---
  const filteredTransactions = useMemo(() => {
    return transactions.filter(t => {
      const matchSearch = !searchQuery || 
        t.description?.toLowerCase().includes(searchQuery.toLowerCase()) || 
        t.heading?.toLowerCase().includes(searchQuery.toLowerCase());
      const matchAccount = filterAccount === "All" || t.account === filterAccount;
      const matchType = filterType === "All" || t.type === filterType;
      
      let matchMonth = true;
      if (filterMonth !== "All" && t.date) {
        const d = new Date(t.date);
        const monthStr = `${MONTH_NAMES[d.getMonth()]} ${d.getFullYear()}`;
        matchMonth = monthStr === filterMonth;
      }

      let matchDate = true;
      if (t.date) {
        const txDate = new Date(t.date);
        if (filterDateFrom && txDate < new Date(filterDateFrom)) matchDate = false;
        if (filterDateTo && txDate > new Date(filterDateTo)) matchDate = false;
      }

      return matchSearch && matchAccount && matchType && matchMonth && matchDate;
    });
  }, [transactions, searchQuery, filterAccount, filterType, filterMonth, filterDateFrom, filterDateTo]);

  // --- Pagination Engine ---
  const totalPages = Math.ceil(filteredTransactions.length / rowsPerPage) || 1;
  const paginatedData = filteredTransactions.slice((currentPage - 1) * rowsPerPage, currentPage * rowsPerPage);

  const inTotal = filteredTransactions.filter(t => t.type === 'Credit').reduce((s, t) => s + Number(t.amount || 0), 0);
  const outTotal = filteredTransactions.filter(t => t.type === 'Debit').reduce((s, t) => s + Number(t.amount || 0), 0);

  // --- Handlers ---
  const handleActionMenu = (item: any) => {
    setActionMenuTx(item); // Just opens our sleek bottom menu now
  };

  const startEdit = () => {
    const item = actionMenuTx;
    const d = new Date(item.date);
    const dateStr = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
    setEditForm({ ...item, amount: String(item.amount), date: dateStr });
    setEditingTx(item);
    setActionMenuTx(null); // Close the action menu
  };

  const confirmDelete = () => {
    const id = actionMenuTx.id;
    setActionMenuTx(null); // Close the action menu
    
    // We keep the native alert ONLY for the final "Are you sure?" 
    // because it's a destructive action and prevents accidental deletions.
    Alert.alert("Delete Transaction", "Are you sure? This cannot be undone.", [
      { text: "Cancel", style: "cancel" },
      { text: "Delete", style: "destructive", onPress: () => executeDelete(id) }
    ]);
  };

  const executeDelete = async (id: number) => {
    try {
      const token = await AsyncStorage.getItem('dt_token');
      const res = await fetch(`${API_URL}/transactions/${id}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (res.ok) fetchTransactions(); // Refresh the list!
      else Alert.alert("Error", "Failed to delete.");
    } catch (e) {
      Alert.alert("Error", "Network error.");
    }
  };

  const handleSaveEdit = async () => {
    if (!editForm.amount || isNaN(Number(editForm.amount)) || !editForm.heading.trim()) {
      Alert.alert("Hold up!", "Please enter a valid amount and category.");
      return;
    }
    setSaving(true);
    try {
      const token = await AsyncStorage.getItem('dt_token');
      const payload = { ...editForm, amount: parseFloat(editForm.amount) };
      
      const res = await fetch(`${API_URL}/transactions/${editingTx.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify(payload)
      });

      if (res.ok) {
        setEditingTx(null);
        fetchTransactions(); // Refresh the list!
      } else {
        Alert.alert("Error", "Failed to update.");
      }
    } catch (e) {
      Alert.alert("Error", "Network error.");
    } finally {
      setSaving(false);
    }
  };

  const fetchTransactions = async () => {
    try {
      const token = await AsyncStorage.getItem('dt_token');
      // Fetching the 100 most recent transactions
      const res = await fetch(`${API_URL}/transactions?limit=500&offset=0`, {
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
      <TouchableOpacity 
        style={styles.txCard} 
        activeOpacity={0.7}
        onPress={() => handleActionMenu(item)}
      >
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
          <View style={{ alignItems: 'flex-end', marginRight: 10, flexShrink: 1 }}>
            <Text style={[styles.txAmount, { color: amountColor }]}>
              {item.type === 'Debit' ? '-' : '+'}{formatMoney(item.amount)}
            </Text>
            {item.description ? (
              <Text 
                style={styles.txDesc} 
                numberOfLines={1} 
                ellipsizeMode="tail"
              >
                {item.description}
              </Text>
            ) : null}
          </View>
          <Ionicons name="ellipsis-vertical" size={18} color="#475569" />
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
    <KeyboardAvoidingView 
      style={styles.container} 
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 20}
    >
      {/* Dynamic Header */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Transactions</Text>
        <TouchableOpacity 
          onPress={() => setShowFilters(!showFilters)} 
          style={[styles.badge, { flexDirection: 'row', alignItems: 'center' }, showFilters && {backgroundColor: '#6366f1'}]}
        >
          <Ionicons name="filter" size={14} color={showFilters ? "#fff" : "#94a3b8"} />
          <Text style={[styles.badgeText, showFilters && {color: '#fff'}, {marginLeft: 6}]}>
            {showFilters ? "Close Filters" : "Filters"}
          </Text>
        </TouchableOpacity>
      </View>

      {/* Elegant Filter Card */}
      {showFilters && (
        <View style={styles.filtersCard}>
          <TextInput
            style={styles.searchInput}
            placeholder="🔍 Search descriptions or categories..."
            placeholderTextColor="#64748b"
            value={searchQuery}
            onChangeText={setSearchQuery}
          />

          {/* DATE RANGE */}
          <Text style={styles.filterLabel}>DATE RANGE</Text>
          <View style={styles.dateRow}>
            <TouchableOpacity style={styles.dateBtn} onPress={() => setShowDatePicker('from')}>
              <Text style={filterDateFrom ? styles.dateTextActive : styles.dateText}>
                {filterDateFrom || "Start Date"}
              </Text>
            </TouchableOpacity>
            <Text style={{color: '#475569'}}>→</Text>
            <TouchableOpacity style={styles.dateBtn} onPress={() => setShowDatePicker('to')}>
              <Text style={filterDateTo ? styles.dateTextActive : styles.dateText}>
                {filterDateTo || "End Date"}
              </Text>
            </TouchableOpacity>
            {(filterDateFrom || filterDateTo) && (
              <TouchableOpacity onPress={() => { setFilterDateFrom(""); setFilterDateTo(""); }} style={{padding: 5}}>
                <Ionicons name="close-circle" size={20} color="#f87171" />
              </TouchableOpacity>
            )}
          </View>

          {/* CHIPS */}
          <Text style={styles.filterLabel}>ACCOUNT</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filterScroll}>
            {availableAccounts.map(a => (
              <TouchableOpacity key={a} style={[styles.filterChip, filterAccount === a && styles.filterChipActive]} onPress={() => setFilterAccount(a)}>
                <Text style={[styles.filterChipText, filterAccount === a && styles.filterChipTextActive]}>{a}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>

          <Text style={styles.filterLabel}>TYPE</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filterScroll}>
            {availableTypes.map(t => (
              <TouchableOpacity key={t} style={[styles.filterChip, filterType === t && styles.filterChipActive]} onPress={() => setFilterType(t)}>
                <Text style={[styles.filterChipText, filterType === t && styles.filterChipTextActive]}>{t}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>

          <Text style={styles.filterLabel}>MONTH</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filterScroll}>
            {availableMonths.map(m => (
              <TouchableOpacity key={m} style={[styles.filterChip, filterMonth === m && styles.filterChipActive]} onPress={() => setFilterMonth(m)}>
                <Text style={[styles.filterChipText, filterMonth === m && styles.filterChipTextActive]}>{m}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
        </View>
      )}

      {/* Floating Stats Pill (Creates perfect separation!) */}
      {filteredTransactions.length > 0 && (
        <View style={styles.statsWrapper}>
          <Text style={styles.statsFound}>{filteredTransactions.length} Found</Text>
          <View style={styles.statsPill}>
            <Text style={styles.statsText}><Text style={{color: '#34d399'}}>+{formatMoney(inTotal)}</Text> In</Text>
            <View style={styles.statsDivider} />
            <Text style={styles.statsText}><Text style={{color: '#f87171'}}>-{formatMoney(outTotal)}</Text> Out</Text>
          </View>
        </View>
      )}

      {/* Native Date Picker Overlay */}
      {showDatePicker && (
        <DateTimePicker
          value={new Date()} // Default to today if nothing selected
          mode="date"
          display="default"
          onChange={onDateChange}
          themeVariant="dark"
        />
      )}

     <FlatList
        data={paginatedData}
        keyExtractor={(item) => item.id.toString()}
        renderItem={renderTransaction}
        contentContainerStyle={styles.listContent}
        
        // --- ADD THESE 3 LINES ---
        keyboardShouldPersistTaps="handled" 
        keyboardDismissMode="on-drag"
        automaticallyAdjustKeyboardInsets={true} 
        // -------------------------

        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#6366f1" />}
        ListEmptyComponent={
          <Text style={styles.emptyText}>📭 No transactions match your filters.</Text>
        }
        ListFooterComponent={
          filteredTransactions.length > 0 ? (
            <View style={styles.paginationContainer}>
              <TouchableOpacity
                style={[styles.pageBtn, currentPage === 1 && styles.pageBtnDisabled]}
                disabled={currentPage === 1}
                onPress={() => setCurrentPage(c => c - 1)}
              >
                <Text style={styles.pageBtnText}>← Prev</Text>
              </TouchableOpacity>
              <Text style={styles.pageText}>Page {currentPage} of {totalPages}</Text>
              <TouchableOpacity
                style={[styles.pageBtn, currentPage === totalPages && styles.pageBtnDisabled]}
                disabled={currentPage === totalPages}
                onPress={() => setCurrentPage(c => c + 1)}
              >
                <Text style={styles.pageBtnText}>Next →</Text>
              </TouchableOpacity>
            </View>
          ) : null
        }
      />

      {/* --- SLEEK ACTION MENU (BOTTOM SHEET) --- */}
      <Modal visible={!!actionMenuTx} animationType="fade" transparent>
        <TouchableOpacity style={styles.modalOverlay} activeOpacity={1} onPress={() => setActionMenuTx(null)}>
          <View style={[styles.modalContent, { paddingBottom: 30 }]}>
            <View style={styles.actionMenuHeader}>
              {/* Top Row: Icon, Title, Amount */}
              <View style={styles.actionMenuTitleRow}>
                <View style={styles.actionMenuIconBg}>
                  <Text style={{ fontSize: 24 }}>{actionMenuTx ? BANKS[actionMenuTx.account] : "🏦"}</Text>
                </View>
                <View style={{ flex: 1, marginLeft: 15 }}>
                  <Text style={styles.actionMenuTitle} numberOfLines={1}>{actionMenuTx?.heading}</Text>
                  <Text style={styles.actionMenuDate}>{actionMenuTx ? formatDate(actionMenuTx.date) : ''}</Text>
                </View>
                <View style={{ alignItems: 'flex-end' }}>
                  <Text style={[styles.actionMenuAmount, { color: actionMenuTx?.type === 'Debit' ? '#f87171' : (actionMenuTx?.type === 'Credit' ? '#34d399' : '#6366f1') }]}>
                    {actionMenuTx?.type === 'Debit' ? '-' : '+'}{actionMenuTx ? formatMoney(actionMenuTx.amount) : ''}
                  </Text>
                </View>
              </View>
              
              {/* Middle Row: Full Note (if it exists) */}
              {actionMenuTx?.description ? (
                <View style={styles.actionMenuNoteBox}>
                  <Text style={styles.actionMenuNoteText}>📝  {actionMenuTx.description}</Text>
                </View>
              ) : null}

              {/* Bottom Row: Badges */}
              <View style={styles.actionMenuDetailsRow}>
                <View style={styles.detailBadge}>
                  <Text style={styles.detailBadgeText}>{actionMenuTx?.account}</Text>
                </View>
                <View style={styles.detailBadge}>
                  <Text style={styles.detailBadgeText}>{actionMenuTx?.type}</Text>
                </View>
              </View>
            </View>

            <TouchableOpacity style={styles.actionMenuBtn} onPress={startEdit}>
              <Ionicons name="pencil" size={20} color="#e2e8f0" style={{ marginRight: 15 }} />
              <Text style={styles.actionMenuText}>Edit Transaction</Text>
            </TouchableOpacity>

            <TouchableOpacity style={[styles.actionMenuBtn, { borderBottomWidth: 0 }]} onPress={confirmDelete}>
              <Ionicons name="trash" size={20} color="#f87171" style={{ marginRight: 15 }} />
              <Text style={[styles.actionMenuText, { color: '#f87171' }]}>Delete Transaction</Text>
            </TouchableOpacity>
          </View>
        </TouchableOpacity>
      </Modal>

      {/* --- EDIT MODAL --- */}
      <Modal visible={!!editingTx} animationType="slide" transparent>
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <View style={styles.modalHeader}>
              <Text style={styles.modalTitle}>Edit Transaction</Text>
              <TouchableOpacity onPress={() => setEditingTx(null)}>
                <Text style={styles.modalClose}>✕</Text>
              </TouchableOpacity>
            </View>

            <Text style={styles.label}>AMOUNT (₹)</Text>
            <TextInput style={styles.input} keyboardType="decimal-pad" value={editForm.amount} onChangeText={(t) => setEditForm({...editForm, amount: t})} />

            <Text style={styles.label}>CATEGORY</Text>
            <TextInput style={styles.input} value={editForm.heading} onChangeText={(t) => setEditForm({...editForm, heading: t})} />

            <Text style={styles.label}>DATE (YYYY-MM-DD)</Text>
            <TextInput style={styles.input} value={editForm.date} onChangeText={(t) => setEditForm({...editForm, date: t})} />

            <Text style={styles.label}>NOTE</Text>
            <TextInput style={styles.input} value={editForm.description} onChangeText={(t) => setEditForm({...editForm, description: t})} />

            <TouchableOpacity style={[styles.saveBtn, saving && {opacity: 0.7}]} onPress={handleSaveEdit} disabled={saving}>
              {saving ? <ActivityIndicator color="#fff" /> : <Text style={styles.saveBtnText}>Save Changes</Text>}
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
    </KeyboardAvoidingView>
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
  
  txRight: { flexDirection: 'row', alignItems: 'center', maxWidth: '45%' },  txAmount: { fontSize: 16, fontWeight: 'bold', fontFamily: 'System' },
  txDesc: { color: '#64748b', fontSize: 11, marginTop: 4 },

  emptyText: { color: '#64748b', textAlign: 'center', marginTop: 40, fontSize: 14 },
  // --- MODAL STYLES ---
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'flex-end' },
  modalContent: { backgroundColor: '#0f172a', borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 24, paddingBottom: 40, borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)' },
  modalHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 },
  modalTitle: { fontSize: 20, fontWeight: 'bold', color: '#fff' },
  modalClose: { fontSize: 24, color: '#64748b', fontWeight: 'bold' },
  label: { fontSize: 12, color: '#64748b', fontWeight: 'bold', marginBottom: 8, marginTop: 10, letterSpacing: 0.5 },
  input: { backgroundColor: '#1e293b', borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)', borderRadius: 10, padding: 12, color: '#fff', fontSize: 16 },
  saveBtn: { backgroundColor: '#6366f1', paddingVertical: 14, borderRadius: 12, alignItems: 'center', marginTop: 24 },
  saveBtnText: { color: '#fff', fontSize: 16, fontWeight: 'bold' },
  actionMenuHeader: { marginBottom: 15, paddingBottom: 20, borderBottomWidth: 1, borderBottomColor: 'rgba(255,255,255,0.05)' },
  actionMenuTitleRow: { flexDirection: 'row', alignItems: 'center', width: '100%', marginBottom: 15 },
  actionMenuIconBg: { width: 50, height: 50, borderRadius: 25, backgroundColor: 'rgba(255,255,255,0.03)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)', alignItems: 'center', justifyContent: 'center' },
  actionMenuAmount: { fontSize: 20, fontWeight: 'bold', fontFamily: 'System' },
  actionMenuDate: { fontSize: 13, color: '#64748b', marginTop: 4 },
  actionMenuBtn: { flexDirection: 'row', alignItems: 'center', paddingVertical: 16, borderBottomWidth: 1, borderBottomColor: 'rgba(255,255,255,0.05)' },
  actionMenuText: { fontSize: 16, color: '#e2e8f0', fontWeight: '500' },
  actionMenuTitle: { fontSize: 18, fontWeight: 'bold', color: '#fff' }, // Just in case this got wiped too!
  actionMenuNoteBox: { backgroundColor: 'rgba(255,255,255,0.02)', padding: 12, borderRadius: 10, marginBottom: 15, borderWidth: 1, borderColor: 'rgba(255,255,255,0.03)' },
  actionMenuNoteText: { color: '#cbd5e1', fontSize: 14, fontStyle: 'italic' },
  
  actionMenuDetailsRow: { flexDirection: 'row', gap: 10, width: '100%' },
  detailBadge: { backgroundColor: '#1e293b', paddingHorizontal: 12, paddingVertical: 6, borderRadius: 8, borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)' },
  detailBadgeText: { color: '#94a3b8', fontSize: 12, fontWeight: '600' },
  // --- FILTER & PAGINATION STYLES ---
  filtersCard: { 
    backgroundColor: '#0f172a', 
    marginHorizontal: 20, 
    borderRadius: 16, 
    padding: 16, 
    marginBottom: 20, 
    borderWidth: 1, 
    borderColor: 'rgba(255,255,255,0.05)',
    shadowColor: '#000', shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.2, shadowRadius: 8, elevation: 5
  },
  searchInput: { backgroundColor: '#1e293b', borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)', borderRadius: 10, padding: 12, color: '#fff', fontSize: 14, marginBottom: 15 },
  filterLabel: { fontSize: 10, color: '#64748b', fontWeight: 'bold', marginBottom: 8, letterSpacing: 0.5 },
  filterScroll: { flexDirection: 'row', marginBottom: 15 },
  filterChip: { paddingHorizontal: 14, paddingVertical: 8, borderRadius: 16, backgroundColor: '#1e293b', borderWidth: 1, borderColor: 'rgba(255,255,255,0.03)', marginRight: 8 },
  filterChipActive: { backgroundColor: 'rgba(99,102,241,0.15)', borderColor: '#6366f1' },
  filterChipText: { color: '#94a3b8', fontSize: 13, fontWeight: '500' },
  filterChipTextActive: { color: '#a5b4fc', fontWeight: 'bold' },

  // Date Range UI
  dateRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 15, backgroundColor: '#1e293b', padding: 8, borderRadius: 12, borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)' },
  dateBtn: { flex: 1, alignItems: 'center', paddingVertical: 8 },
  dateText: { color: '#64748b', fontSize: 14, fontWeight: '500' },
  dateTextActive: { color: '#e2e8f0', fontSize: 14, fontWeight: 'bold' },

  // Stats Pill Separation
  statsWrapper: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginHorizontal: 20, marginBottom: 15 },
  statsFound: { color: '#64748b', fontSize: 13, fontWeight: '600' },
  statsPill: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#111827', paddingHorizontal: 14, paddingVertical: 8, borderRadius: 20, borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)' },
  statsDivider: { width: 1, height: 12, backgroundColor: 'rgba(255,255,255,0.1)', marginHorizontal: 10 },
  statsText: { color: '#94a3b8', fontSize: 13, fontWeight: '600' },

  // Pagination
  paginationContainer: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 10, paddingBottom: 40 },
  pageBtn: { paddingVertical: 10, paddingHorizontal: 16, backgroundColor: '#1e293b', borderRadius: 10, borderWidth: 1, borderColor: 'rgba(255,255,255,0.1)' },
  pageBtnDisabled: { opacity: 0.4 },
  pageBtnText: { color: '#e2e8f0', fontSize: 14, fontWeight: '600' },
  pageText: { color: '#64748b', fontSize: 13, fontWeight: '500' },
});