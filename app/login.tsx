import React, { useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ActivityIndicator, Alert } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useRouter } from 'expo-router';
import { GoogleSignin } from '@react-native-google-signin/google-signin';

// 1. Import Firebase auth modules
import { initializeApp } from 'firebase/app';
import { 
  initializeAuth, 
  GoogleAuthProvider, 
  signInWithCredential 
} from 'firebase/auth';
// @ts-ignore - Firebase TS definitions are missing this, but the function exists at runtime
import { getReactNativePersistence } from 'firebase/auth';
import ReactNativeAsyncStorage from '@react-native-async-storage/async-storage';

const API_URL = "https://sabarishhh14-dailytrack-v2.hf.space/api";

// 2. Paste your Firebase Config from your web App.jsx here
const firebaseConfig = {
  apiKey: "AIzaSyCUtU8xA7T_jK-sczn82WcTGhzoC-oaawk",
  authDomain: "dailytrack-629bf.firebaseapp.com",
  projectId: "dailytrack-629bf",
  storageBucket: "dailytrack-629bf.firebasestorage.app",
  messagingSenderId: "68900020784",
  appId: "68900020784:web:92abd259e8bb6fd87e83f5"
};

// Initialize Firebase
const firebaseApp = initializeApp(firebaseConfig);
// This tells Firebase exactly where to store your session on the S24
const auth = initializeAuth(firebaseApp, {
  persistence: getReactNativePersistence(ReactNativeAsyncStorage)
});
// 🛑 KEEP YOUR FIREBASE WEB CLIENT ID HERE
GoogleSignin.configure({
  webClientId: '68900020784-s98hgjb235573iga5db1bprubs173ghb.apps.googleusercontent.com',
});

export default function LoginScreen() {
  const [loading, setLoading] = useState(false);
  const router = useRouter();

  const handleGoogleLogin = async () => {
    setLoading(true);
    try {
      await GoogleSignin.hasPlayServices();
      
      // Step A: Get the Google Token
      const userInfo = await GoogleSignin.signIn();
      const idToken = (userInfo as any).idToken || (userInfo as any).data?.idToken;
      if (!idToken) throw new Error("No ID token received from Google.");

      // Step B: Exchange Google Token for a Firebase Credential
      const credential = GoogleAuthProvider.credential(idToken);

      // Step C: Sign in to Firebase with that credential
      const firebaseResult = await signInWithCredential(auth, credential);

      // Step D: Extract the FIREBASE ID Token (This is what Flask wants!)
      const firebaseIdToken = await firebaseResult.user.getIdToken();

      // Step E: Send the Firebase Token to your backend
      const res = await fetch(`${API_URL}/auth/firebase-login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id_token: firebaseIdToken }) // <-- Sending the Firebase token
      });

      const data = await res.json();
      
      if (data.success) {
        await AsyncStorage.setItem('dt_token', data.token);
        router.replace('/' as any); 
      } else {
        Alert.alert("Access Denied", data.message);
      }

    } catch (error: any) {
      console.error(error);
      Alert.alert("Sign-In Error", error.message || "Something went wrong.");
    } finally {
      setLoading(false);
    }
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