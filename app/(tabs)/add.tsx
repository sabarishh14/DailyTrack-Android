import AsyncStorage from '@react-native-async-storage/async-storage';
import DateTimePicker from '@react-native-community/datetimepicker';
import { useRouter } from 'expo-router';
import React, { useState } from 'react';
import { ActivityIndicator, Alert, KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';

// 🛑 REMEMBER TO UPDATE THIS IP ADDRESS!
const API_URL ="https://sabarishhh14-dailytrack-v2.hf.space/api";

const BANKS = ["KOTAK", "IDBI", "FEDERAL", "CUB", "INDIAN", "ICICI", "CC-PINNACLE 6360", "Cash"];
const BANK_EMOJIS: Record<string, string> = { KOTAK: "🔴", IDBI: "🟢", FEDERAL: "🟠", CUB: "🟣", INDIAN: "🔵", ICICI: "🟡", "CC-PINNACLE 6360": "💳", "Cash": "💵" };

const CATEGORIES = [
  "Aasai", "Card Fees", "Charges", "Cinema", "Clothing", "CBE Trip",
  "Daily Need", "Donation", "Education", "Electricity", "Entertainment",
  "FD", "Flowers", "Food", "Fruits", "God", "Grocery", "Haircut",
  "Income", "Interest", "Internet", "Investment", "Kudremukh Trip",
  "Laundry", "Loan", "Locker", "Medical", "Msc", "Parking", "Petrol",
  "Popcorn", "Savings", "Snacks", "Spotify", "Sports", "Tally", "Test",
  "Tips", "Transport", "Veggies", "YT Premium"
];

export default function AddScreen() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);

  // Form State
  // Draft Queue State
  const [queue, setQueue] = useState<any[]>([]);
  const [amount, setAmount] = useState("");
  const [type, setType] = useState("Debit");
  const [account, setAccount] = useState("KOTAK");
  const [heading, setHeading] = useState("");
  const [description, setDescription] = useState("");
  
  // Date string formatted as YYYY-MM-DD
  const [dateObj, setDateObj] = useState(new Date());
  const [showDatePicker, setShowDatePicker] = useState(false);
  const date = dateObj.toISOString().split('T')[0]; // Keeps your payload happy!

  // State for dynamic autocompletes
  const [recentNotes, setRecentNotes] = useState<string[]>([]);
  const [recentCategories, setRecentCategories] = useState<string[]>([]);

  // Fetch recent transactions on mount to populate pills and dropdowns
  React.useEffect(() => {
    const fetchRecentData = async () => {
      try {
        const token = await AsyncStorage.getItem('dt_token');
        const res = await fetch(`${API_URL}/transactions?limit=100&offset=0`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        
        if (res.ok) {
          const data = await res.json();
          const txs = data.transactions || [];

          // 1. Extract unique notes
          const uniqueNotes = [...new Set(
            txs.map((t: any) => t.description)
              .filter((desc: string) => desc && desc.trim() !== '')
          )] as string[];
          setRecentNotes(uniqueNotes);

          // 2. Extract unique recent categories
          const uniqueCategories = [...new Set(
            txs.map((t: any) => t.heading)
              .filter((head: string) => head && head.trim() !== '')
          )] as string[];
          
          // Slice to 10 so the pill scroll doesn't go on forever!
          setRecentCategories(uniqueCategories.slice(0, 10)); 
        }
      } catch (e) {
        console.log("Failed to fetch recent data", e);
      }
    };
    fetchRecentData();
  }, []);

  // Note Autocomplete Filter
  const filteredNotes = recentNotes.filter(n => 
    description.length > 0 && 
    n.toLowerCase().includes(description.toLowerCase()) && 
    n !== description
  );

  const onChangeDate = (event: any, selectedDate?: Date) => {
    setShowDatePicker(Platform.OS === 'ios');
    if (selectedDate) setDateObj(selectedDate);
  };

  // Category Autocomplete Logic
  const filteredCategories = CATEGORIES.filter(c => 
    heading.length > 0 && 
    c.toLowerCase().includes(heading.toLowerCase()) && 
    c !== heading
  );

  const handleDirectSave = async () => {
    if (!amount || isNaN(Number(amount)) || !heading.trim()) {
      Alert.alert("Hold up!", "Please enter a valid amount and category.");
      return;
    }

    setLoading(true);
    try {
      const token = await AsyncStorage.getItem('dt_token');
      // Single transaction in the array payload
      const payload = [{
        account,
        date,
        type,
        heading: heading.trim(),
        description: description.trim(),
        amount: parseFloat(amount)
      }];

      const res = await fetch(`${API_URL}/transactions`, {
        method: "POST",
        headers: { 
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}` 
        },
        body: JSON.stringify(payload),
      });

      if (res.ok) {
        setAmount("");
        setHeading("");
        setDescription("");
        Alert.alert("Success!", "Transaction saved.", [
          { text: "Awesome", onPress: () => router.push('/money' as any) }
        ]);
      } else {
        Alert.alert("Error", "Failed to save transaction.");
      }
    } catch (error: any) {
      Alert.alert("Network Error", error.message);
    } finally {
      setLoading(false);
    }
  };

  const handleAddToQueue = () => {
    if (!amount || isNaN(Number(amount)) || !heading.trim()) {
      Alert.alert("Hold up!", "Please enter a valid amount and category.");
      return;
    }

    const newItem = {
      id: Date.now().toString(), // local ID for rendering/deletion
      account,
      date,
      type,
      heading: heading.trim(),
      description: description.trim(),
      amount: parseFloat(amount)
    };

    setQueue([newItem, ...queue]); // Push to top of the queue

    // Smart Reset: Clear the input, keep the Date & Bank Account
    setAmount("");
    setHeading("");
    setDescription("");
  };

  const handleRemoveFromQueue = (id: string) => {
    setQueue(queue.filter(item => item.id !== id));
  };

  const handleSaveAll = async () => {
    if (queue.length === 0) return;
    setLoading(true);
    
    try {
      const token = await AsyncStorage.getItem('dt_token');
      // Strip out our temporary local 'id' before sending to the backend
      const payload = queue.map(({ id, ...rest }) => rest);

      const res = await fetch(`${API_URL}/transactions`, {
        method: "POST",
        headers: { 
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}` 
        },
        body: JSON.stringify(payload),
      });

      if (res.ok) {
        setQueue([]); // Clear queue on success
        Alert.alert("Success!", `Saved ${payload.length} transactions.`, [
          { text: "Awesome", onPress: () => router.push('/money' as any) }
        ]);
      } else {
        const errText = await res.text();
        Alert.alert("Error", "Failed to save transactions.");
        console.error(errText);
      }
    } catch (error: any) {
      Alert.alert("Network Error", error.message);
    } finally {
      setLoading(false);
    }
  };

  // Check if either dropdown is actively showing
  const showCategoryDropdown = heading.length > 0 && filteredCategories.length > 0;
  const showNoteDropdown = description.length > 0 && filteredNotes.length > 0;
  const isDropdownActive = showCategoryDropdown || showNoteDropdown;

  return (
    <KeyboardAvoidingView 
      style={styles.container} 
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 60 : 20}
    >
      <ScrollView 
        contentContainerStyle={[
          styles.scrollContent, 
          { paddingBottom: isDropdownActive ? 250 : 60 } // Expands ONLY when needed!
        ]} 
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        
        <View style={styles.header}>
          <Text style={styles.headerTitle}>New Transaction</Text>
        </View>

        {/* --- AMOUNT INPUT --- */}
        <View style={styles.amountContainer}>
          <Text style={styles.currencySymbol}>₹</Text>
          <TextInput
            style={styles.amountInput}
            placeholder="0.00"
            placeholderTextColor="#475569"
            keyboardType="decimal-pad"
            value={amount}
            onChangeText={setAmount}
            autoFocus
          />
        </View>

        {/* --- TRANSACTION TYPE --- */}
        <View style={styles.typeRow}>
          {['Debit', 'Credit', 'Savings'].map((t) => (
            <TouchableOpacity 
              key={t}
              style={[
                styles.typeBtn, 
                type === t && (styles[`typeBtnActive${t}` as keyof typeof styles] as any)
              ]}
              onPress={() => setType(t)}
            >
              <Text style={[
                styles.typeBtnText, 
                type === t && (styles[`typeTextActive${t}` as keyof typeof styles] as any)
              ]}>
                {t}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* --- ACCOUNT SELECTOR (Horizontal Scroll) --- */}
        <Text style={styles.label}>ACCOUNT</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipScroll}>
          {BANKS.map((b) => (
            <TouchableOpacity 
              key={b} 
              style={[styles.chip, account === b && styles.chipActive]}
              onPress={() => setAccount(b)}
            >
              <Text style={[styles.chipText, account === b && styles.chipTextActive]}>
                {BANK_EMOJIS[b]} {b}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>

        {/* --- CATEGORY SELECTOR (Horizontal Scroll) --- */}
        <Text style={styles.label}>CATEGORY</Text>
        {recentCategories.length > 0 && (
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipScroll}>
            {recentCategories.map((c) => (
              <TouchableOpacity 
                key={c} 
                style={[styles.chip, heading === c && styles.chipActive]}
                onPress={() => setHeading(c)}
              >
                <Text style={[styles.chipText, heading === c && styles.chipTextActive]}>
                  {c}
                </Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
        )}
        {/* Custom Category Input & Autocomplete Dropdown */}
        <TextInput
          style={styles.input}
          placeholder="Or type custom category..."
          placeholderTextColor="#64748b"
          value={heading}
          onChangeText={setHeading}
        />
        {filteredCategories.length > 0 && (
          <View style={styles.dropdown}>
            {filteredCategories.slice(0, 3).map((c) => ( // <-- Added .slice(0, 3)
              <TouchableOpacity key={c} style={styles.dropdownItem} onPress={() => setHeading(c)}>
                <Text style={styles.dropdownText}>{c}</Text>
              </TouchableOpacity>
            ))}
          </View>
        )}

        {/* --- DATE & NOTE --- */}
        <Text style={styles.label}>DATE</Text>
        <TouchableOpacity 
          style={styles.input} 
          onPress={() => setShowDatePicker(true)}
        >
          <Text style={{ color: '#fff', fontSize: 16 }}>{date}</Text>
        </TouchableOpacity>

        {showDatePicker && (
          <DateTimePicker
            value={dateObj}
            mode="date"
            display="default"
            onChange={onChangeDate}
            themeVariant="dark" // Forces dark mode for iOS picker
          />
        )}

        <Text style={styles.label}>NOTE (OPTIONAL)</Text>
        <TextInput
          style={styles.input}
          placeholder="What was this for?"
          placeholderTextColor="#64748b"
          value={description}
          onChangeText={setDescription}
        />
        {filteredNotes.length > 0 && (
          <View style={styles.dropdown}>
            {filteredNotes.slice(0, 3).map((n) => ( // <-- Added .slice(0, 3)
              <TouchableOpacity key={n} style={styles.dropdownItem} onPress={() => setDescription(n)}>
                <Text style={styles.dropdownText}>{n}</Text>
              </TouchableOpacity>
            ))}
          </View>
        )}

        {/* --- ACTION BUTTONS --- */}
        <View style={styles.actionRow}>
          <TouchableOpacity 
            style={[styles.directSaveBtn, loading && { opacity: 0.7 }]} 
            onPress={handleDirectSave}
            disabled={loading}
          >
            {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.directSaveBtnText}>Save Now</Text>}
          </TouchableOpacity>

          <TouchableOpacity 
            style={styles.addNextBtn} 
            onPress={handleAddToQueue}
            disabled={loading}
          >
            <Text style={styles.addNextBtnText}>+ Queue</Text>
          </TouchableOpacity>
        </View>

        {/* --- THE DRAFT QUEUE --- */}
        {queue.length > 0 && (
          <View style={styles.queueContainer}>
            <View style={styles.queueHeaderRow}>
              <Text style={styles.queueTitle}>Draft Queue ({queue.length})</Text>
            </View>

            {queue.map((item, index) => (
              <View key={item.id} style={styles.queueCard}>
                <View style={styles.queueLeft}>
                  <Text style={styles.queueBank}>{BANK_EMOJIS[item.account]}</Text>
                  <View>
                    <Text style={styles.queueHeading}>{item.heading}</Text>
                    <Text style={styles.queueSub}>{item.type} • {item.date}</Text>
                  </View>
                </View>
                
                <View style={styles.queueRight}>
                  <Text style={[styles.queueAmount, { color: item.type === 'Debit' ? '#f87171' : (item.type === 'Credit' ? '#34d399' : '#818cf8') }]}>
                    {item.type === 'Debit' ? '-' : '+'}₹{item.amount}
                  </Text>
                  <TouchableOpacity onPress={() => handleRemoveFromQueue(item.id)} style={styles.queueDelete}>
                    <Text style={{color: '#64748b', fontSize: 16}}>✕</Text>
                  </TouchableOpacity>
                </View>
              </View>
            ))}
            
            {/* --- SAVE ALL BUTTON --- */}
            <TouchableOpacity 
              style={[styles.submitBtn, loading && { opacity: 0.7 }]} 
              onPress={handleSaveAll}
              disabled={loading}
            >
              {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.submitBtnText}>Save All to DailyTrack</Text>}
            </TouchableOpacity>
          </View>
        )}

      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080b12' },
  scrollContent: { padding: 20, paddingBottom: 60 },
  header: { marginTop: 40, marginBottom: 30 },
  headerTitle: { fontSize: 24, fontWeight: 'bold', color: '#fff' },

  amountContainer: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', marginBottom: 30 },
  currencySymbol: { fontSize: 40, fontWeight: 'bold', color: '#64748b', marginRight: 10 },
  amountInput: { fontSize: 56, fontWeight: '800', color: '#fff', minWidth: 150, textAlign: 'center' },

  typeRow: { flexDirection: 'row', gap: 10, marginBottom: 30 },
  typeBtn: { flex: 1, paddingVertical: 12, borderRadius: 10, backgroundColor: '#111827', borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)', alignItems: 'center' },
  typeBtnText: { color: '#64748b', fontWeight: '600' },
  
  typeBtnActiveDebit: { backgroundColor: 'rgba(248,113,113,0.1)', borderColor: '#f87171' },
  typeTextActiveDebit: { color: '#f87171' },
  
  typeBtnActiveCredit: { backgroundColor: 'rgba(52,211,153,0.1)', borderColor: '#34d399' },
  typeTextActiveCredit: { color: '#34d399' },
  
  typeBtnActiveSavings: { backgroundColor: 'rgba(99,102,241,0.1)', borderColor: '#6366f1' },
  typeTextActiveSavings: { color: '#818cf8' },

  label: { fontSize: 12, color: '#64748b', fontWeight: 'bold', marginBottom: 10, marginTop: 10, letterSpacing: 0.5 },
  
  chipScroll: { flexDirection: 'row', marginBottom: 15, paddingBottom: 5 },
  chip: { paddingHorizontal: 16, paddingVertical: 10, borderRadius: 20, backgroundColor: '#111827', borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)', marginRight: 10 },
  chipActive: { backgroundColor: 'rgba(99,102,241,0.15)', borderColor: '#6366f1' },
  chipText: { color: '#94a3b8', fontSize: 14, fontWeight: '500' },
  chipTextActive: { color: '#a5b4fc', fontWeight: 'bold' },

  input: { backgroundColor: '#111827', borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)', borderRadius: 12, padding: 14, color: '#fff', fontSize: 16, marginBottom: 20 },

  submitBtn: { backgroundColor: '#6366f1', paddingVertical: 16, borderRadius: 14, alignItems: 'center', marginTop: 20, shadowColor: '#6366f1', shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.3, shadowRadius: 10, elevation: 5 },
  submitBtnText: { color: '#fff', fontSize: 16, fontWeight: 'bold' },
  dropdown: { 
    backgroundColor: '#1e293b', 
    borderRadius: 12, 
    borderWidth: 1, 
    borderColor: 'rgba(255,255,255,0.1)', 
    marginTop: -12, // Pulls it slightly up towards the input
    marginBottom: 20, 
    overflow: 'hidden',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 5,
    elevation: 5,
  },
  dropdownItem: { 
    paddingVertical: 14, 
    paddingHorizontal: 16,
    borderBottomWidth: 1, 
    borderBottomColor: 'rgba(255,255,255,0.05)' 
  },
  dropdownText: { color: '#e2e8f0', fontSize: 15, fontWeight: '500' },
  actionRow: { flexDirection: 'row', gap: 12, marginTop: 10 },
  
  directSaveBtn: { flex: 2, backgroundColor: '#6366f1', paddingVertical: 16, borderRadius: 12, alignItems: 'center', shadowColor: '#6366f1', shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.3, shadowRadius: 10, elevation: 5 },
  directSaveBtnText: { color: '#fff', fontSize: 16, fontWeight: 'bold' },
  
  addNextBtn: { flex: 1, backgroundColor: 'rgba(99,102,241,0.1)', borderWidth: 1, borderColor: '#6366f1', paddingVertical: 16, borderRadius: 12, alignItems: 'center' },
  addNextBtnText: { color: '#818cf8', fontSize: 15, fontWeight: 'bold' },

  queueContainer: { marginTop: 30, paddingTop: 20, borderTopWidth: 1, borderTopColor: 'rgba(255,255,255,0.05)' },
  queueHeaderRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 15 },
  queueTitle: { color: '#e2e8f0', fontSize: 14, fontWeight: 'bold' },
  
  queueCard: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#111827', padding: 12, borderRadius: 12, marginBottom: 10, borderWidth: 1, borderColor: 'rgba(255,255,255,0.03)' },
  queueLeft: { flexDirection: 'row', alignItems: 'center' },
  queueBank: { fontSize: 18, marginRight: 10 },
  queueHeading: { color: '#fff', fontSize: 14, fontWeight: '600' },
  queueSub: { color: '#64748b', fontSize: 11, marginTop: 2 },
  queueRight: { flexDirection: 'row', alignItems: 'center' },
  queueAmount: { fontSize: 15, fontWeight: 'bold', marginRight: 12 },
  queueDelete: { padding: 4 },
});