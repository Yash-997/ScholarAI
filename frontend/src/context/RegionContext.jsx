import React, { createContext, useContext, useState, useEffect } from 'react';

const RegionContext = createContext();

export const ALL_INDIAN_STATES = [
  'All India',
  'Andhra Pradesh',
  'Arunachal Pradesh',
  'Assam',
  'Bihar',
  'Chhattisgarh',
  'Goa',
  'Gujarat',
  'Haryana',
  'Himachal Pradesh',
  'Jharkhand',
  'Karnataka',
  'Kerala',
  'Madhya Pradesh',
  'Maharashtra',
  'Manipur',
  'Meghalaya',
  'Mizoram',
  'Nagaland',
  'Odisha',
  'Punjab',
  'Rajasthan',
  'Sikkim',
  'Tamil Nadu',
  'Telangana',
  'Tripura',
  'Uttar Pradesh',
  'Uttarakhand',
  'West Bengal',
  'Andaman and Nicobar Islands',
  'Chandigarh',
  'Dadra and Nagar Haveli and Daman and Diu',
  'Delhi',
  'Jammu and Kashmir',
  'Ladakh',
  'Lakshadweep',
  'Puducherry'
];

export const SUPPORTED_REGIONS = [
  { id: 'All India', name: 'All India (National & CSR)' },
  { id: 'Maharashtra', name: 'Maharashtra (MahaDBT)' },
  { id: 'Karnataka', name: 'Karnataka (SSP Portal)' },
  { id: 'Uttar Pradesh', name: 'Uttar Pradesh (Scholarship Portal)' },
  { id: 'Gujarat', name: 'Gujarat (Digital Gujarat / MYSY)' },
  { id: 'Bihar', name: 'Bihar (PMS Online)' },
  { id: 'Andhra Pradesh', name: 'Andhra Pradesh (Jnanabhumi)' }
];

export function RegionProvider({ children }) {
  const [selectedRegion, setSelectedRegion] = useState(() => {
    return localStorage.getItem('scholar_selected_region') || 'Maharashtra';
  });

  useEffect(() => {
    localStorage.setItem('scholar_selected_region', selectedRegion);
  }, [selectedRegion]);

  return (
    <RegionContext.Provider
      value={{
        selectedRegion,
        setSelectedRegion,
        SUPPORTED_REGIONS,
        ALL_INDIAN_STATES
      }}
    >
      {children}
    </RegionContext.Provider>
  );
}

export function useRegion() {
  const context = useContext(RegionContext);
  if (!context) {
    throw new Error('useRegion must be used within a RegionProvider');
  }
  return context;
}
