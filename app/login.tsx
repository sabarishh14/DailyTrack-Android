import AsyncStorage from '@react-native-async-storage/async-storage';
import { useRouter } from 'expo-router';
import React, { useState } from 'react';
import { ActivityIndicator, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

export default function LoginScreen() {
  
  const [loading, setLoading] = useState(false);
  const router = useRouter();

  const handleGoogleLogin = async () => {
    
    setLoading(true);
    // Simulating a network request delay
    setTimeout(async () => {
      // Inside handleGoogleLogin in app/login.tsx
      await AsyncStorage.setItem('dt_token', 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJzYnNhYmFyaXNoMTRAZ21haWwuY29tIiwiaWF0IjoxNzczODM3MDYwLCJleHAiOjE3NzY0MjkwNjB9.9FQpPYqv6ETSDdpAOWIN4CzJ1m9CZyYkSIH6MwbdvkQ');
      setLoading(false);
      router.replace('/' as any);
    }, 500);
  };

  return (
    <View style={styles.container}>
      <View style={styles.card}>
        <View style={styles.header}>
          <Text style={styles.logoName}>DailyTrack</Text>
          <Text style={styles.logoSub}>Personal Dashboard</Text>
        </View>

        <TouchableOpacity 
          style={[styles.button, loading && styles.buttonDisabled]} 
          onPress={handleGoogleLogin}
          disabled={loading}
        >
          {loading ? (
            <ActivityIndicator color="#e2e8f0" />
          ) : (
            <Text style={styles.buttonText}>Sign in with Google</Text>
          )}
        </TouchableOpacity>

        <Text style={styles.footerText}>
          Only authorized Google accounts can access this dashboard.
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080b12', alignItems: 'center', justifyContent: 'center', padding: 20 },
  card: { backgroundColor: '#111827', borderColor: 'rgba(255,255,255,0.07)', borderWidth: 1, borderRadius: 20, padding: 40, width: '100%', maxWidth: 380, alignItems: 'center' },
  header: { alignItems: 'center', marginBottom: 30 },
  logoName: { fontSize: 32, fontWeight: '800', color: '#6366f1', marginBottom: 5 },
  logoSub: { fontSize: 14, color: '#64748b' },
  button: { width: '100%', alignItems: 'center', justifyContent: 'center', backgroundColor: '#1e293b', borderColor: 'rgba(255,255,255,0.1)', borderWidth: 1, paddingVertical: 14, borderRadius: 12, marginBottom: 20 },
  buttonDisabled: { opacity: 0.7 },
  buttonText: { color: '#e2e8f0', fontSize: 16, fontWeight: '600' },
  footerText: { fontSize: 12, color: '#64748b', textAlign: 'center' }
});