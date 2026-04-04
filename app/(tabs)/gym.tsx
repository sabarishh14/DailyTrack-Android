import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity, ActivityIndicator, RefreshControl, Modal, TextInput, Alert, KeyboardAvoidingView, Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Ionicons } from '@expo/vector-icons';

// 🛑 UPDATE THIS!
const API_URL ="https://sabarishhh14-dailytrack-v2.hf.space/api";

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export default function GymScreen() {
  const [physical, setPhysical] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  // Time Navigation
  const [currentDate, setCurrentDate] = useState(new Date());
  
  // Modal State
  const [isModalVisible, setModalVisible] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({
    date: new Date().toISOString().split('T')[0],
    gym: false, badminton: false, table_tennis: false, cricket: false, others: false, description: ''
  });

  const fetchData = async () => {
    try {
      const token = await AsyncStorage.getItem('dt_token');
      const res = await fetch(`${API_URL}/physical`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (res.ok) setPhysical(await res.json());
    } catch (error) {
      console.error("Failed to fetch gym data:", error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => { fetchData(); }, []);
  const onRefresh = useCallback(() => { setRefreshing(true); fetchData(); }, []);

  // Filter & Stats Logic
  const displayMonth = currentDate.getMonth();
  const displayYear = currentDate.getFullYear();
  
  const filteredRecords = physical.filter(p => {
    if (!p.date) return false;
    const d = new Date(p.date);
    return d.getMonth() === displayMonth && d.getFullYear() === displayYear;
  });

  const daysActive = filteredRecords.filter(p => 
    p.gym || p.badminton || p.table_tennis || p.cricket || p.others
  ).length;

  const changeMonth = (offset: number) => {
    const newDate = new Date(currentDate);
    newDate.setMonth(newDate.getMonth() + offset);
    setCurrentDate(newDate);
  };

  const handleSaveActivity = async () => {
    setSaving(true);
    try {
      const token = await AsyncStorage.getItem('dt_token');
      const res = await fetch(`${API_URL}/physical`, {
        method: "POST",
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify(form)
      });
      if (res.ok) {
        setModalVisible(false);
        fetchData(); // Refresh the list
        setForm({ ...form, description: '', gym: false, badminton: false, table_tennis: false, cricket: false, others: false }); // Reset
      } else {
        Alert.alert("Error", "Failed to log activity.");
      }
    } catch (e: any) {
      Alert.alert("Error", e.message);
    } finally {
      setSaving(false);
    }
  };

  const toggleActivity = (key: keyof typeof form) => {
    setForm(prev => ({ ...prev, [key]: !prev[key] }));
  };

  const renderActivity = ({ item }: { item: any }) => {
    const d = new Date(item.date);
    const dateStr = `${d.getDate()} ${MONTHS[d.getMonth()]}`;

    return (
      <View style={styles.activityCard}>
        <View style={styles.cardHeader}>
          <Text style={styles.cardDate}>📅 {dateStr}</Text>
          <View style={styles.iconRow}>
            {item.gym ? <Text style={styles.emoji}>🏋️</Text> : null}
            {item.badminton ? <Text style={styles.emoji}>🏸</Text> : null}
            {item.table_tennis ? <Text style={styles.emoji}>🏓</Text> : null}
            {item.cricket ? <Text style={styles.emoji}>🏏</Text> : null}
            {item.others ? <Text style={styles.emoji}>🏃‍♂️</Text> : null}
          </View>
        </View>
        {item.description ? <Text style={styles.cardDesc}>{item.description}</Text> : null}
      </View>
    );
  };

  if (loading) {
    return <View style={styles.centerContainer}><ActivityIndicator size="large" color="#06b6d4" /></View>;
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Gym & Activity</Text>
        <TouchableOpacity onPress={() => setModalVisible(true)} style={styles.addButton}>
          <Ionicons name="add" size={20} color="#fff" />
          <Text style={styles.addButtonText}>Log</Text>
        </TouchableOpacity>
      </View>

      {/* --- STATS & MONTH NAVIGATION --- */}
      <View style={styles.statsCard}>
        <View style={styles.monthNav}>
          <TouchableOpacity onPress={() => changeMonth(-1)} style={styles.navBtn}>
            <Ionicons name="chevron-back" size={24} color="#06b6d4" />
          </TouchableOpacity>
          <Text style={styles.monthText}>{MONTHS[displayMonth]} {displayYear}</Text>
          <TouchableOpacity onPress={() => changeMonth(1)} style={styles.navBtn}>
            <Ionicons name="chevron-forward" size={24} color="#06b6d4" />
          </TouchableOpacity>
        </View>
        <Text style={styles.bigNumber}>{daysActive}</Text>
        <Text style={styles.subLabel}>Days Active</Text>
      </View>

      {/* --- ACTIVITY LIST --- */}
      <FlatList
        data={filteredRecords}
        keyExtractor={(item) => item.id.toString()}
        renderItem={renderActivity}
        contentContainerStyle={styles.listContent}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#06b6d4" />}
        ListEmptyComponent={<Text style={styles.emptyText}>No activity logged for this month.</Text>}
      />

      {/* --- ADD ACTIVITY MODAL --- */}
      <Modal visible={isModalVisible} animationType="slide" transparent={true}>
        <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : "height"} style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <View style={styles.modalHeader}>
              <Text style={styles.modalTitle}>Log Activity</Text>
              <TouchableOpacity onPress={() => setModalVisible(false)}><Ionicons name="close" size={28} color="#94a3b8" /></TouchableOpacity>
            </View>

            <Text style={styles.label}>DATE (YYYY-MM-DD)</Text>
            <TextInput style={styles.input} value={form.date} onChangeText={(text) => setForm({...form, date: text})} />

            <Text style={styles.label}>ACTIVITIES</Text>
            <View style={styles.toggleGrid}>
              {[
                { key: 'gym', icon: '🏋️', label: 'Gym' },
                { key: 'badminton', icon: '🏸', label: 'Badminton' },
                { key: 'table_tennis', icon: '🏓', label: 'TT' },
                { key: 'cricket', icon: '🏏', label: 'Cricket' },
                { key: 'others', icon: '🏃‍♂️', label: 'Others' }
              ].map((act) => (
                <TouchableOpacity 
                  key={act.key} 
                  style={[styles.toggleBtn, form[act.key as keyof typeof form] && styles.toggleBtnActive]}
                  onPress={() => toggleActivity(act.key as keyof typeof form)}
                >
                  <Text style={{ fontSize: 18 }}>{act.icon}</Text>
                  <Text style={[styles.toggleText, form[act.key as keyof typeof form] && styles.toggleTextActive]}>{act.label}</Text>
                </TouchableOpacity>
              ))}
            </View>

            <Text style={styles.label}>NOTES (OPTIONAL)</Text>
            <TextInput 
              style={styles.input} 
              placeholder="Leg day, 5km run..." 
              placeholderTextColor="#64748b"
              value={form.description} 
              onChangeText={(text) => setForm({...form, description: text})} 
            />

            <TouchableOpacity style={styles.saveBtn} onPress={handleSaveActivity} disabled={saving}>
              {saving ? <ActivityIndicator color="#fff" /> : <Text style={styles.saveBtnText}>Save Activity</Text>}
            </TouchableOpacity>
          </View>
        </KeyboardAvoidingView>
      </Modal>

    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080b12' },
  centerContainer: { flex: 1, backgroundColor: '#080b12', justifyContent: 'center', alignItems: 'center' },
  header: { marginTop: 60, marginBottom: 20, paddingHorizontal: 20, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  headerTitle: { fontSize: 24, fontWeight: 'bold', color: '#fff' },
  addButton: { flexDirection: 'row', backgroundColor: 'rgba(6, 182, 212, 0.15)', paddingVertical: 6, paddingHorizontal: 12, borderRadius: 8, alignItems: 'center', borderWidth: 1, borderColor: 'rgba(6, 182, 212, 0.3)' },
  addButtonText: { color: '#06b6d4', fontWeight: 'bold', marginLeft: 4 },

  statsCard: { backgroundColor: '#111827', marginHorizontal: 20, borderRadius: 16, padding: 20, alignItems: 'center', borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)', marginBottom: 20 },
  monthNav: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', width: '100%', marginBottom: 10 },
  navBtn: { padding: 5 },
  monthText: { color: '#fff', fontSize: 16, fontWeight: 'bold', textTransform: 'uppercase', letterSpacing: 1 },
  bigNumber: { fontSize: 48, fontWeight: '900', color: '#06b6d4' },
  subLabel: { color: '#64748b', fontSize: 12, fontWeight: 'bold', textTransform: 'uppercase', marginTop: 5 },

  listContent: { paddingHorizontal: 20, paddingBottom: 40 },
  activityCard: { backgroundColor: '#111827', borderRadius: 12, padding: 16, marginBottom: 12, borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)' },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 },
  cardDate: { color: '#e2e8f0', fontWeight: 'bold', fontSize: 14 },
  iconRow: { flexDirection: 'row', gap: 5 },
  emoji: { fontSize: 18 },
  cardDesc: { color: '#94a3b8', fontSize: 13, marginTop: 4 },
  emptyText: { color: '#64748b', textAlign: 'center', marginTop: 40 },

  // Modal Styles
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.7)', justifyContent: 'flex-end' },
  modalContent: { backgroundColor: '#111827', borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 24, paddingBottom: 40 },
  modalHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 },
  modalTitle: { fontSize: 20, fontWeight: 'bold', color: '#fff' },
  label: { fontSize: 12, color: '#64748b', fontWeight: 'bold', marginBottom: 8, marginTop: 10, letterSpacing: 0.5 },
  input: { backgroundColor: '#1e293b', borderWidth: 1, borderColor: 'rgba(255,255,255,0.1)', borderRadius: 10, padding: 14, color: '#fff', fontSize: 16, marginBottom: 15 },
  
  toggleGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginBottom: 15 },
  toggleBtn: { flexBasis: '30%', backgroundColor: '#1e293b', paddingVertical: 12, borderRadius: 10, alignItems: 'center', borderWidth: 1, borderColor: 'rgba(255,255,255,0.05)' },
  toggleBtnActive: { backgroundColor: 'rgba(6, 182, 212, 0.15)', borderColor: '#06b6d4' },
  toggleText: { color: '#94a3b8', fontSize: 12, fontWeight: '600', marginTop: 4 },
  toggleTextActive: { color: '#06b6d4' },

  saveBtn: { backgroundColor: '#06b6d4', paddingVertical: 16, borderRadius: 12, alignItems: 'center', marginTop: 10 },
  saveBtnText: { color: '#fff', fontSize: 16, fontWeight: 'bold' }
});