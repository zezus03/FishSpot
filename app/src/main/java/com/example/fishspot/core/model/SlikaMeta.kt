package com.example.fishspot.core.model

import com.google.firebase.storage.StorageMetadata

// Oznaka tipa za svaku sliku koja ide u Storage; pravila (storage.rules) primaju samo image/*
val SLIKA_JPEG: StorageMetadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
