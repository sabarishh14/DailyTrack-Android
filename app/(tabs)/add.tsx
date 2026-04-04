import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ScrollView, ActivityIndicator, Alert, KeyboardAvoidingView, Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useRouter } from 'expo-router';

// 🛑 REMEMBER TO UPDATE THIS IP ADDRESS!
const API_URL ="https://sabarishhh14-dailytrack-v2.hf.space/api";

const BANKS = ["KOTAK", "IDBI", "FEDERAL", "CUB", "INDIAN", "ICICI", "CC-PINNACLE 6360", "Cash"];
const BANK_EMOJIS: Record<string, string> = { KOTAK: "🔴", IDBI: "🟢", FEDERAL: "🟠", CUB: "🟣", INDIAN: "🔵", ICICI: "🟡", "CC-PINNACLE 6360": "💳", "Cash": "💵" };

const CATEGORIES = [
  "Food", "Snacks", "Petrol", "Transport", "Medical", "Daily Need", 
  "Clothing", "Entertainment", "Cinema", "Spotify", "Income", "Salary"
];

export default function AddScreen() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);

  // Form State
  const [amount, setAmount] = useState("");
  const [type, setType] = useState("Debit");
  const [account, setAccount] = useState("KOTAK");
  const [heading, setHeading] = useState("");
  const [description, setDescription] = useState("");
  
  // Date string formatted as YYYY-MM-DD
  const [date, setDate] = useState(new Date().toISOString().split('T')[0]);

  const handleSubmit = async () => {
    if (!amount || isNaN(Number(amount)) || !heading.trim()) {
      Alert.alert("Hold up!", "Please enter a valid amount and select a category.");
      return;
    }

    setLoading(true);
    try {
      const token = await AsyncStorage.getItem('dt_token');
      
      // Your Flask backend expects an array of transaction objects
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
        // Reset form
        setAmount("");
        setHeading("");
        setDescription("");
        
        Alert.alert("Success!", "Transaction added successfully.", [
          { text: "Awesome", onPress: () => router.push('/money' as any) }
        ]);
      } else {
        const errText = await res.text();
        Alert.alert("Error", "Failed to save transaction.");
        console.error(errText);
      }
    } catch (error: any) {
      Alert.alert("Network Error", error.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView 
      style={styles.container} 
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
        
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
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipScroll}>
          {CATEGORIES.map((c) => (
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
        {/* Custom Category Input if it's not in the quick list */}
        <TextInput
          style={styles.input}
          placeholder="Or type custom category..."
          placeholderTextColor="#64748b"
          value={heading}
          onChangeText={setHeading}
        />

        {/* --- DATE & NOTE --- */}
        <Text style={styles.label}>DATE (YYYY-MM-DD)</Text>
        <TextInput
          style={styles.input}
          value={date}
          onChangeText={setDate}
        />

        <Text style={styles.label}>NOTE (OPTIONAL)</Text>
        <TextInput
          style={styles.input}
          placeholder="What was this for?"
          placeholderTextColor="#64748b"
          value={description}
          onChangeText={setDescription}
        />

        {/* --- SUBMIT BUTTON --- */}
        <TouchableOpacity 
          style={[styles.submitBtn, loading && { opacity: 0.7 }]} 
          onPress={handleSubmit}
          disabled={loading}
        >
          {loading ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.submitBtnText}>Save Transaction</Text>
          )}
        </TouchableOpacity>

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
  amountInput: { fontSize: 56, fontWeight: '800', color: '#fff', minWidth: 150 },

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
  submitBtnText: { color: '#fff', fontSize: 16, fontWeight: 'bold' }
});