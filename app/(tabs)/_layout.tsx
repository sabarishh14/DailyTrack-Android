import { Tabs, Redirect } from 'expo-router';
import React, { useState, useEffect } from 'react';
import { View, ActivityIndicator } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

// Expo comes pre-installed with these awesome icon sets!
import { Ionicons, FontAwesome5 } from '@expo/vector-icons'; 

export default function TabLayout() {
  const [isLoading, setIsLoading] = useState(true);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  useEffect(() => {
    const checkAuth = async () => {
      const token = await AsyncStorage.getItem('dt_token');
      if (token) {
        setIsAuthenticated(true);
      }
      setIsLoading(false);
    };
    checkAuth();
  }, []);

  if (isLoading) {
    return (
      <View style={{ flex: 1, backgroundColor: '#080b12', justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator size="large" color="#6366f1" />
      </View>
    );
  }

  if (!isAuthenticated) {
    return <Redirect href={"/login" as any} />;
  }

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: '#6366f1', // var(--accent)
        tabBarInactiveTintColor: '#64748b', // var(--text2)
        tabBarStyle: {
          backgroundColor: '#0d1117',
          borderTopColor: 'rgba(255,255,255,0.07)',
          paddingBottom: 12, // Increased from 5 to give text breathing room
          paddingTop: 8,
          height: 70, // Increased from 60
        },
        tabBarLabelStyle: {
          fontSize: 10,
          fontFamily: 'System',
          fontWeight: '600',
        }
      }}>
      
      <Tabs.Screen
        name="index"
        options={{
          title: 'Home',
          tabBarIcon: ({ color }) => <Ionicons name="home" size={24} color={color} />,
        }}
      />
      
      <Tabs.Screen
        name="money"
        options={{
          title: 'Money',
          tabBarIcon: ({ color }) => <Ionicons name="wallet" size={24} color={color} />,
        }}
      />
      
      <Tabs.Screen
        name="add"
        options={{
          title: 'Add',
          tabBarIcon: ({ color }) => (
            // Making the Add button pop out a bit more!
            <Ionicons name="add-circle" size={34} color={color} style={{ marginTop: -5 }} />
          ),
        }}
      />
      
      <Tabs.Screen
        name="gym"
        options={{
          title: 'Gym',
          tabBarIcon: ({ color }) => <FontAwesome5 name="dumbbell" size={20} color={color} />,
        }}
      />
      
      <Tabs.Screen
        name="invest"
        options={{
          title: 'Invest',
          tabBarIcon: ({ color }) => <Ionicons name="trending-up" size={24} color={color} />,
        }}
      />
      
    </Tabs>
  );
}