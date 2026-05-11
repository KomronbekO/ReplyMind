from __future__ import annotations

import torch
from torch import nn


class MLPClassifier(nn.Module):
    """Two-layer MLP head over a frozen 384-d MiniLM embedding."""

    def __init__(self, in_dim: int = 384, hidden: int = 256, num_classes: int = 6, p_drop: float = 0.2):
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(in_dim, hidden),
            nn.ReLU(),
            nn.Dropout(p_drop),
            nn.Linear(hidden, num_classes),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.net(x)
