import pandas as pd
scenarios =[
        [0, 0, 0],  # No enabled services in either scan
        [1, 0, 0],  # One trusted service remains enabled
        [2, 0, 0],  # Two trusted services remain enabled
        [0, 0, 0],  # User disables the only enabled service
        [1, 1, 0],  # User intentionally enables Select to Speak
        [2, 1, 0],  # User adds a trusted service alongside an existing one
        [2, 2, 0],  # User enables two trusted services during device setup
        [1, 1, 0],  # User replaces one trusted service with another

        [1, 1, 1],  # A fake support caller persuades the user to enable a service
        [2, 1, 1],  # A scammer gets a new service enabled alongside a trusted one
        [2, 2, 1],  # A scammer persuades the user to enable two services
        [3, 2, 1],  # Two services are enabled under pressure while one remains active
]
columns=[
    "accessibility_count_old",
    "accessibility_count_new",
    "risk_label",
]
df=pd.DataFrame(scenarios, columns=columns)
print("Synthetic Accessibility scenarios")
print(df.to_string(index=False))

features = [
    "accessibility_count_old",
    "accessibility_count_new"
]

conflicts = df.groupby(features)["risk_label"].nunique()
print(conflicts[conflicts > 1])

print("\nExamples per risk class")
print(df["risk_label"].value_counts().sort_index().to_string())