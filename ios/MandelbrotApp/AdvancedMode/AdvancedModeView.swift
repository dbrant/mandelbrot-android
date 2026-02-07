import SwiftUI

struct AdvancedModeView: View {
    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(systemName: "cpu")
                .font(.system(size: 64))
                .foregroundColor(.gray)

            Text("Experimental Rendering")
                .font(.title)
                .fontWeight(.bold)

            Text("This mode uses GPU-based perturbation theory rendering with arbitrary-precision arithmetic (GMP/MPFR libraries) for deep zooming beyond double-precision limits.")
                .multilineTextAlignment(.center)
                .foregroundColor(.secondary)
                .padding(.horizontal, 32)

            Text("Coming soon")
                .font(.title3)
                .foregroundColor(.accentColor)
                .padding(.top, 8)

            Spacer()
        }
        .navigationBarTitleDisplayMode(.inline)
    }
}
