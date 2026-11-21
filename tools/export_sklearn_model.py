#!/usr/bin/env python3
"""
Exports the fitted scikit-learn IsolationForest in NoGinx's model.pkl into
data/model.json, where Java can load the exact fitted tree structure.
"""

import argparse
import json
from pathlib import Path

import joblib


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("model_pkl", type=Path)
    parser.add_argument("output_json", type=Path)
    args = parser.parse_args()

    model = joblib.load(args.model_pkl)
    exported = {
        "format": "noginx-sklearn-isolation-forest-v1",
        "n_estimators": int(model.n_estimators),
        "max_samples": int(model.max_samples_),
        "n_features": int(model.n_features_in_),
        "offset": float(model.offset_),
        "estimators_features": [
            features.tolist() for features in model.estimators_features_
        ],
        "trees": [],
    }

    for estimator in model.estimators_:
        tree = estimator.tree_
        exported["trees"].append({
            "children_left": tree.children_left.tolist(),
            "children_right": tree.children_right.tolist(),
            "feature": tree.feature.tolist(),
            "threshold": tree.threshold.tolist(),
            "n_node_samples": tree.n_node_samples.tolist(),
        })

    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(exported, separators=(",", ":")),
        encoding="utf-8",
    )
    print(
        f"Exported {len(exported['trees'])} fitted trees "
        f"with {sum(len(t['children_left']) for t in exported['trees'])} nodes"
    )


if __name__ == "__main__":
    main()
