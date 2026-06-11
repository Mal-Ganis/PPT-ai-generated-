package com.example.pptbackend.service;

import com.example.pptbackend.dto.FactCheckDetailDto;
import java.util.List;

public record FactVerificationResult(double rate, List<FactCheckDetailDto> details) {
}
